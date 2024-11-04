/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
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
import static feign.Util.ensureClosed;

import feign.InvocationHandlerFactory.MethodHandler;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import feign.vertx.VertxHttpClient;
import io.vertx.core.Future;
import io.vertx.core.VertxException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Method handler for asynchronous HTTP requests via {@link VertxHttpClient}. Inspired by {@link
 * SynchronousMethodHandler}.
 *
 * @author Alexei KLENIN
 * @author Gordon McKinney
 */
final class VertxMethodHandler implements MethodHandler {
  private static final long MAX_RESPONSE_BUFFER_SIZE = 8192L;

  private final MethodMetadata metadata;
  private final Target<?> target;
  private final VertxHttpClient client;
  private final Retryer retryer;
  private final List<RequestInterceptor> requestInterceptors;
  private final Logger logger;
  private final Logger.Level logLevel;
  private final RequestTemplate.Factory buildTemplateFromArgs;
  private final Decoder decoder;
  private final ErrorDecoder errorDecoder;
  private final boolean decode404;

  private VertxMethodHandler(
      final Target<?> target,
      final VertxHttpClient client,
      final Retryer retryer,
      final List<RequestInterceptor> requestInterceptors,
      final Logger logger,
      final Logger.Level logLevel,
      final MethodMetadata metadata,
      final RequestTemplate.Factory buildTemplateFromArgs,
      final Decoder decoder,
      final ErrorDecoder errorDecoder,
      final boolean decode404) {
    this.target = target;
    this.client = client;
    this.retryer = retryer;
    this.requestInterceptors = requestInterceptors;
    this.logger = logger;
    this.logLevel = logLevel;
    this.metadata = metadata;
    this.buildTemplateFromArgs = buildTemplateFromArgs;
    this.errorDecoder = errorDecoder;
    this.decoder = decoder;
    this.decode404 = decode404;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Future<?> invoke(final Object[] argv) {
    final RequestTemplate template = buildTemplateFromArgs.create(argv);
    final Retryer retryer = this.retryer.clone();

    final RetryRecoverer recoverer = new RetryRecoverer<>(template, retryer);
    return executeAndDecode(template).recover(recoverer);
  }

  /**
   * Executes request from {@code template} with {@code this.client} and decodes the response.
   * Result or occurred error wrapped in returned Future.
   *
   * @param template request template
   * @return future with decoded result or occurred error
   */
  private Future<Object> executeAndDecode(final RequestTemplate template) {
    final Request request = targetRequest(template);

    logRequest(request);

    final Instant start = Instant.now();

    return client
        .execute(request)
        .compose(
            response -> {
              final long elapsedTime = Duration.between(start, Instant.now()).toMillis();
              boolean shouldClose = true;

              try {
                // TODO: check why this buffering is needed
                if (logLevel != Logger.Level.NONE) {
                  response =
                      logger.logAndRebufferResponse(
                          metadata.configKey(), logLevel, response, elapsedTime);
                }

                if (Response.class == metadata.returnType()) {
                  if (response.body() == null) {
                    return Future.succeededFuture(response);
                  } else if (response.body().length() == null
                      || response.body().length() > MAX_RESPONSE_BUFFER_SIZE) {
                    shouldClose = false;
                    return Future.succeededFuture(response);
                  } else {
                    return Future.succeededFuture(
                        Response.builder()
                            .status(response.status())
                            .reason(response.reason())
                            .headers(response.headers())
                            .request(response.request())
                            .body(response.body())
                            .build());
                  }
                } else if (response.status() >= 200 && response.status() < 300) {
                  if (Void.class == metadata.returnType()) {
                    return Future.succeededFuture();
                  } else {
                    return Future.succeededFuture(decode(response, request));
                  }
                } else if (decode404 && response.status() == 404) {
                  return Future.succeededFuture(decoder.decode(response, metadata.returnType()));
                } else {
                  return Future.failedFuture(errorDecoder.decode(metadata.configKey(), response));
                }
              } catch (final IOException ioException) {
                logIoException(ioException, elapsedTime);
                return Future.failedFuture(errorReading(request, response, ioException));
              } catch (FeignException exception) {
                return Future.failedFuture(exception);
              } finally {
                if (shouldClose) {
                  ensureClosed(response.body());
                }
              }
            },
            failure -> {
              if (failure instanceof VertxException || failure instanceof TimeoutException) {
                return Future.failedFuture(failure);
              } else if (failure.getCause() instanceof IOException) {
                final long elapsedTime = Duration.between(start, Instant.now()).toMillis();
                logIoException((IOException) failure.getCause(), elapsedTime);
                return Future.failedFuture(
                    errorExecuting(request, (IOException) failure.getCause()));
              } else {
                return Future.failedFuture(failure.getCause());
              }
            });
  }

  /**
   * Associates request to defined target.
   *
   * @param template request template
   * @return fully formed request
   */
  private Request targetRequest(final RequestTemplate template) {
    for (final RequestInterceptor interceptor : requestInterceptors) {
      interceptor.apply(template);
    }

    return target.apply(template);
  }

  /**
   * Transforms HTTP response body into object using decoder.
   *
   * @param response HTTP response
   * @param request HTTP request
   * @return decoded result
   * @throws IOException IO exception during the reading of InputStream of response
   * @throws DecodeException when decoding failed due to a checked or unchecked exception besides
   *     IOException
   * @throws FeignException when decoding succeeds, but conveys the operation failed
   */
  private Object decode(final Response response, final Request request)
      throws IOException, FeignException {
    try {
      return decoder.decode(response, metadata.returnType());
    } catch (final FeignException feignException) {
      /* All feign exception including decode exceptions */
      throw feignException;
    } catch (final RuntimeException unexpectedException) {
      /* Any unexpected exception */
      throw new DecodeException(-1, unexpectedException.getMessage(), request, unexpectedException);
    }
  }

  /**
   * Logs request.
   *
   * @param request HTTP request
   */
  private void logRequest(final Request request) {
    if (logLevel != Logger.Level.NONE) {
      logger.logRequest(metadata.configKey(), logLevel, request);
    }
  }

  /**
   * Logs IO exception.
   *
   * @param exception IO exception
   * @param elapsedTime time spent to execute request
   */
  private void logIoException(final IOException exception, final long elapsedTime) {
    if (logLevel != Logger.Level.NONE) {
      logger.logIOException(metadata.configKey(), logLevel, exception, elapsedTime);
    }
  }

  /** Logs retry. */
  private void logRetry() {
    if (logLevel != Logger.Level.NONE) {
      logger.logRetry(metadata.configKey(), logLevel);
    }
  }

  static final class Factory {
    private final VertxHttpClient client;
    private final Retryer retryer;
    private final List<RequestInterceptor> requestInterceptors;
    private final Logger logger;
    private final Logger.Level logLevel;
    private final boolean decode404;

    Factory(
        final VertxHttpClient client,
        final Retryer retryer,
        final List<RequestInterceptor> requestInterceptors,
        final Logger logger,
        final Logger.Level logLevel,
        final boolean decode404) {
      this.client = client;
      this.retryer = retryer;
      this.requestInterceptors = requestInterceptors;
      this.logger = logger;
      this.logLevel = logLevel;
      this.decode404 = decode404;
    }

    MethodHandler create(
        final Target<?> target,
        final MethodMetadata metadata,
        final RequestTemplate.Factory buildTemplateFromArgs,
        final Decoder decoder,
        final ErrorDecoder errorDecoder) {
      return new VertxMethodHandler(
          target,
          client,
          retryer,
          requestInterceptors,
          logger,
          logLevel,
          metadata,
          buildTemplateFromArgs,
          decoder,
          errorDecoder,
          decode404);
    }
  }

  /**
   * Handler for failures able to retry execution of request. In this case handler passed to new
   * request.
   *
   * @param <T> type of response
   */
  private final class RetryRecoverer<T> implements Function<Throwable, Future<T>> {
    private final RequestTemplate template;
    private final Retryer retryer;

    private RetryRecoverer(final RequestTemplate template, final Retryer retryer) {
      this.template = template;
      this.retryer = retryer;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Future<T> apply(final Throwable throwable) {
      if (throwable instanceof RetryableException) {
        this.retryer.continueOrPropagate((RetryableException) throwable);
        logRetry();
        return ((Future<T>) executeAndDecode(this.template)).recover(this);
      } else {
        return Future.failedFuture(throwable);
      }
    }
  }
}
