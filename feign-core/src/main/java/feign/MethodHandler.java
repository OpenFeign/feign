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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;

import java.io.IOException;
import java.net.URI;

import javax.inject.Inject;
import javax.inject.Provider;

import feign.Request.Options;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.net.HttpHeaders.LOCATION;
import static feign.FeignException.errorExecuting;
import static feign.FeignException.errorReading;

final class MethodHandler {

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
                                Function<Object[], RequestTemplate> buildTemplateFromArgs, Options options, Decoder decoder, ErrorDecoder errorDecoder) {
      return new MethodHandler(target, client, retryer, wire, md, buildTemplateFromArgs, options, decoder, errorDecoder);
    }
  }

  private final MethodMetadata metadata;
  private final Target<?> target;
  private final Client client;
  private final Provider<Retryer> retryer;
  private final Wire wire;

  private final Function<Object[], RequestTemplate> buildTemplateFromArgs;
  private final Options options;
  private final Decoder decoder;
  private final ErrorDecoder errorDecoder;

  // cannot inject wildcards in dagger
  @SuppressWarnings("rawtypes")
  private MethodHandler(Target target, Client client, Provider<Retryer> retryer, Wire wire, MethodMetadata metadata,
                        Function<Object[], RequestTemplate> buildTemplateFromArgs, Options options, Decoder decoder, ErrorDecoder errorDecoder) {
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

  public Object executeAndDecode(String configKey, RequestTemplate template, TypeToken<?> returnType)
      throws Throwable {
    // create the request from a mutable copy of the input template.
    Request request = target.apply(new RequestTemplate(template));
    wire.wireRequest(target, request);
    Response response = execute(request);
    try {
      response = wire.wireAndRebufferResponse(target, response);
      if (response.status() >= 200 && response.status() < 300) {
        if (returnType.getRawType().equals(Response.class)) {
          return response;
        } else if (returnType.getRawType() == URI.class && !response.body().isPresent()) {
          ImmutableList<String> location = response.headers().get(LOCATION);
          if (!location.isEmpty())
            return URI.create(location.get(0));
        } else if (returnType.getRawType() == void.class) {
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
    if (response.body().isPresent()) {
      try {
        response.body().get().close();
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
