/**
 * Copyright 2012-2019 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign;

import static feign.ExceptionPropagationPolicy.UNWRAP;
import static feign.FeignException.errorExecuting;
import static feign.FeignException.errorReading;
import static feign.Util.checkNotNull;
import static feign.Util.ensureClosed;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import feign.InvocationHandlerFactory.MethodHandler;
import feign.Request.Options;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;

public final class SynchronousMethodHandler implements MethodHandler {

  private static final long MAX_RESPONSE_BUFFER_SIZE = 8192L;

  private final MethodMetadata metadata;
  private final Target<?> target;
  private final RequestTemplate.Factory buildTemplateFromArgs;

  private final FeignConfig feignConfig;

  public SynchronousMethodHandler(
      Target<?> target,
      FeignConfig feignConfig,
      MethodMetadata metadata,
      RequestTemplate.Factory buildTemplateFromArgs) {
    this.target = checkNotNull(target, "target");
    this.feignConfig = checkNotNull(feignConfig, "feignConfig for %s", target);
    this.metadata = checkNotNull(metadata, "metadata for %s", target);
    this.buildTemplateFromArgs = checkNotNull(buildTemplateFromArgs, "metadata for %s", target);
  }

  @Override
  public Object invoke(Object... argv) throws Throwable {
    RequestTemplate template = buildTemplateFromArgs.create(argv);
    Options options = findOptions(argv);
    Retryer retryer = feignConfig.retryer.clone();
    while (true) {
      try {
        return executeAndDecode(template, options);
      } catch (RetryableException e) {
        try {
          retryer.continueOrPropagate(e);
        } catch (RetryableException th) {
          Throwable cause = th.getCause();
          if (feignConfig.propagationPolicy == UNWRAP && cause != null) {
            throw cause;
          } else {
            throw th;
          }
        }
        if (feignConfig.logLevel != Logger.Level.NONE) {
          feignConfig.logger.logRetry(metadata.configKey(), feignConfig.logLevel);
        }
        continue;
      }
    }
  }

  Object executeAndDecode(RequestTemplate template, Options options) throws Throwable {
    final Request request = targetRequest(template);

    if (feignConfig.logLevel != Logger.Level.NONE) {
      feignConfig.logger.logRequest(metadata.configKey(), feignConfig.logLevel, request);
    }

    Response response;
    long start = System.nanoTime();
    try {
      response = feignConfig.client.execute(request, feignConfig.options);
    } catch (IOException e) {
      if (feignConfig.logLevel != Logger.Level.NONE) {
        feignConfig.logger.logIOException(metadata.configKey(), feignConfig.logLevel, e,
            elapsedTime(start));
      }
      throw errorExecuting(request, e);
    }
    long elapsedTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

    boolean shouldClose = true;
    try {
      if (feignConfig.logLevel != Logger.Level.NONE) {
        response =
            feignConfig.logger.logAndRebufferResponse(metadata.configKey(), feignConfig.logLevel,
                response, elapsedTime);
      }
      if (Response.class == metadata.returnType()) {
        if (response.body() == null) {
          return response;
        }
        if (response.body().length() == null ||
            response.body().length() > MAX_RESPONSE_BUFFER_SIZE) {
          shouldClose = false;
          return response;
        }
        // Ensure the response body is disconnected
        byte[] bodyData = Util.toByteArray(response.body().asInputStream());
        return response.toBuilder().body(bodyData).build();
      }
      if (response.status() >= 200 && response.status() < 300) {
        if (void.class == metadata.returnType()) {
          return null;
        } else {
          Object result = decode(response);
          shouldClose = feignConfig.closeAfterDecode;
          return result;
        }
      } else if (feignConfig.decode404 && response.status() == 404
          && void.class != metadata.returnType()) {
        Object result = decode(response);
        shouldClose = feignConfig.closeAfterDecode;
        return result;
      } else {
        throw feignConfig.errorDecoder.decode(metadata.configKey(), response);
      }
    } catch (IOException e) {
      if (feignConfig.logLevel != Logger.Level.NONE) {
        feignConfig.logger.logIOException(metadata.configKey(), feignConfig.logLevel, e,
            elapsedTime);
      }
      throw errorReading(request, response, e);
    } finally {
      if (shouldClose) {
        ensureClosed(response.body());
      }
    }
  }

  long elapsedTime(long start) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
  }

  Request targetRequest(RequestTemplate template) {
    for (RequestInterceptor interceptor : feignConfig.requestInterceptors) {
      interceptor.apply(template);
    }
    return target.apply(template);
  }

  Object decode(Response response) throws Throwable {
    try {
      return feignConfig.decoder.decode(response, metadata.returnType());
    } catch (FeignException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new DecodeException(response.status(), e.getMessage(), e);
    }
  }

  Options findOptions(Object[] argv) {
    if (argv == null || argv.length == 0) {
      return feignConfig.options;
    }
    return (Options) Stream.of(argv)
        .filter(o -> o instanceof Options)
        .findFirst()
        .orElse(feignConfig.options);
  }

  static class Factory {

    private final FeignConfig feignConfig;

    Factory(FeignConfig feignConfig) {
      this.feignConfig = checkNotNull(feignConfig, "feignConfig");
    }

    public MethodHandler create(Target<?> target,
                                MethodMetadata md,
                                RequestTemplate.Factory buildTemplateFromArgs,
                                Options options,
                                Decoder decoder,
                                ErrorDecoder errorDecoder) {
      return new SynchronousMethodHandler(target, feignConfig, md, buildTemplateFromArgs);
    }
  }
}
