/*
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign;

import static feign.FeignException.errorExecuting;
import static feign.FeignException.errorReading;
import static feign.Util.checkNotNull;
import static feign.Util.ensureClosed;

import feign.Request.Options;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Provider;

abstract class MethodHandler {

  /** Those using guava will implement as {@code Function<Object[], RequestTemplate>}. */
  static interface BuildTemplateFromArgs {
    public RequestTemplate apply(Object[] argv);
  }

  static class Factory {

    private final Client client;
    private final Provider<Retryer> retryer;
    private final Wire wire;

    @Inject
    Factory(Client client, Provider<Retryer> retryer, Wire wire) {
      this.client = checkNotNull(client, "client");
      this.retryer = checkNotNull(retryer, "retryer");
      this.wire = checkNotNull(wire, "wire");
    }

    public MethodHandler create(
        Target<?> target,
        MethodMetadata md,
        BuildTemplateFromArgs buildTemplateFromArgs,
        Options options,
        Decoder decoder,
        ErrorDecoder errorDecoder) {
      return new SynchronousMethodHandler(
          target, client, retryer, wire, md, buildTemplateFromArgs, options, decoder, errorDecoder);
    }
  }

  static final class SynchronousMethodHandler extends MethodHandler {
    private final Decoder decoder;

    private SynchronousMethodHandler(
        Target<?> target,
        Client client,
        Provider<Retryer> retryer,
        Wire wire,
        MethodMetadata metadata,
        BuildTemplateFromArgs buildTemplateFromArgs,
        Options options,
        Decoder decoder,
        ErrorDecoder errorDecoder) {
      super(target, client, retryer, wire, metadata, buildTemplateFromArgs, options, errorDecoder);
      this.decoder = checkNotNull(decoder, "decoder for %s", target);
    }

    @Override
    protected Object decode(Object[] argv, Response response) throws Throwable {
      if (metadata.returnType().equals(Response.class)) {
        return response;
      } else if (metadata.returnType() == void.class) {
        return null;
      }
      return decoder.decode(metadata.configKey(), response, metadata.returnType());
    }
  }

  protected final MethodMetadata metadata;
  protected final Target<?> target;
  protected final Client client;
  protected final Provider<Retryer> retryer;
  protected final Wire wire;

  protected final BuildTemplateFromArgs buildTemplateFromArgs;
  protected final Options options;
  protected final ErrorDecoder errorDecoder;

  private MethodHandler(
      Target<?> target,
      Client client,
      Provider<Retryer> retryer,
      Wire wire,
      MethodMetadata metadata,
      BuildTemplateFromArgs buildTemplateFromArgs,
      Options options,
      ErrorDecoder errorDecoder) {
    this.target = checkNotNull(target, "target");
    this.client = checkNotNull(client, "client for %s", target);
    this.retryer = checkNotNull(retryer, "retryer for %s", target);
    this.wire = checkNotNull(wire, "wire for %s", target);
    this.metadata = checkNotNull(metadata, "metadata for %s", target);
    this.buildTemplateFromArgs = checkNotNull(buildTemplateFromArgs, "metadata for %s", target);
    this.options = checkNotNull(options, "options for %s", target);
    this.errorDecoder = checkNotNull(errorDecoder, "errorDecoder for %s", target);
  }

  public Object invoke(Object[] argv) throws Throwable {
    RequestTemplate template = buildTemplateFromArgs.apply(argv);
    Retryer retryer = this.retryer.get();
    while (true) {
      try {
        return executeAndDecode(argv, template);
      } catch (RetryableException e) {
        retryer.continueOrPropagate(e);
        continue;
      }
    }
  }

  public Object executeAndDecode(Object[] argv, RequestTemplate template) throws Throwable {
    // create the request from a mutable copy of the input template.
    Request request = target.apply(new RequestTemplate(template));
    wire.wireRequest(target, request);
    Response response;
    try {
      response = client.execute(request, options);
    } catch (IOException e) {
      throw errorExecuting(request, e);
    }
    try {
      response = wire.wireAndRebufferResponse(target, response);
      if (response.status() >= 200 && response.status() < 300) {
        return decode(argv, response);
      } else {
        throw errorDecoder.decode(metadata.configKey(), response);
      }
    } catch (IOException e) {
      throw errorReading(request, response, e);
    } finally {
      ensureClosed(response.body());
    }
  }

  protected abstract Object decode(Object[] argv, Response response) throws Throwable;
}
