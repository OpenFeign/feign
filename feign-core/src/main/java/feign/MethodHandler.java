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

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;

import javax.inject.Inject;
import javax.inject.Provider;

import feign.Request.Options;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;

import static feign.FeignException.errorExecuting;
import static feign.FeignException.errorReading;
import static feign.Util.LOCATION;
import static feign.Util.checkNotNull;
import static feign.Util.firstOrNull;

final class MethodHandler {

  /**
   * Those using guava will implement as {@code Function<Object[], RequestTemplate>}.
   */
  static interface BuildTemplateFromArgs {
    public RequestTemplate apply(Object[] argv);
  }

  static class Factory {

    private final Client client;
    private final Provider<Retryer> retryer;
    private final Wire wire;

    @Inject Factory(Client client, Provider<Retryer> retryer, Wire wire) {
      this.client = checkNotNull(client, "client");
      this.retryer = checkNotNull(retryer, "retryer");
      this.wire = checkNotNull(wire, "wire");
    }

    public MethodHandler create(Target<?> target, MethodMetadata md,
                                BuildTemplateFromArgs buildTemplateFromArgs, Options options, Decoder decoder, ErrorDecoder errorDecoder) {
      return new MethodHandler(target, client, retryer, wire, md, buildTemplateFromArgs, options, decoder, errorDecoder);
    }
  }

  private final MethodMetadata metadata;
  private final Target<?> target;
  private final Client client;
  private final Provider<Retryer> retryer;
  private final Wire wire;

  private final BuildTemplateFromArgs buildTemplateFromArgs;
  private final Options options;
  private final Decoder decoder;
  private final ErrorDecoder errorDecoder;

  // cannot inject wildcards in dagger
  @SuppressWarnings("rawtypes")
  private MethodHandler(Target target, Client client, Provider<Retryer> retryer, Wire wire, MethodMetadata metadata,
                        BuildTemplateFromArgs buildTemplateFromArgs, Options options, Decoder decoder, ErrorDecoder errorDecoder) {
    this.target = checkNotNull(target, "target");
    this.client = checkNotNull(client, "client for %s", target);
    this.retryer = checkNotNull(retryer, "retryer for %s", target);
    this.wire = checkNotNull(wire, "wire for %s", target);
    this.metadata = checkNotNull(metadata, "metadata for %s", target);
    this.buildTemplateFromArgs = checkNotNull(buildTemplateFromArgs, "metadata for %s", target);
    this.options = checkNotNull(options, "options for %s", target);
    this.decoder = checkNotNull(decoder, "decoder for %s", target);
    this.errorDecoder = checkNotNull(errorDecoder, "errorDecoder for %s", target);
  }

  public Object invoke(Object[] argv) throws Throwable {
    RequestTemplate template = buildTemplateFromArgs.apply(argv);
    Retryer retryer = this.retryer.get();
    while (true) {
      try {
        return executeAndDecode(metadata.configKey(), template, metadata.returnType());
      } catch (RetryableException e) {
        retryer.continueOrPropagate(e);
        continue;
      }
    }
  }

  public Object executeAndDecode(String configKey, RequestTemplate template, Type returnType)
      throws Throwable {
    // create the request from a mutable copy of the input template.
    Request request = target.apply(new RequestTemplate(template));
    wire.wireRequest(target, request);
    Response response = execute(request);
    try {
      response = wire.wireAndRebufferResponse(target, response);
      if (response.status() >= 200 && response.status() < 300) {
        if (returnType.equals(Response.class)) {
          return response;
        } else if (returnType == URI.class && response.body() == null) {
          String location = firstOrNull(response.headers(), LOCATION);
          if (location != null)
            return URI.create(location);
        } else if (returnType == void.class) {
          return null;
        }
        return decoder.decode(configKey, response, returnType);
      } else {
        return errorDecoder.decode(configKey, response, returnType);
      }
    } catch (Throwable e) {
      ensureBodyClosed(response);
      if (IOException.class.isInstance(e))
        throw errorReading(request, response, IOException.class.cast(e));
      throw e;
    }
  }

  private void ensureBodyClosed(Response response) {
    if (response.body() != null) {
      try {
        response.body().close();
      } catch (IOException ignored) { // NOPMD
      }
    }
  }

  private Response execute(Request request) {
    try {
      return client.execute(request, options);
    } catch (IOException e) {
      throw errorExecuting(request, e);
    }
  }
}
