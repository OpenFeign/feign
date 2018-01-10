package feign;

import static feign.FeignException.errorExecuting;
import static feign.FeignException.errorReading;
import static feign.Util.checkNotNull;
import static feign.Util.ensureClosed;

import feign.InvocationHandlerFactory.MethodHandler;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import feign.vertx.VertxHttpClient;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientOptions;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Method handler for asynchronous HTTP requests via {@link VertxHttpClient}.
 * Inspired by {@link SynchronousMethodHandler}.
 *
 * @author Alexei KLENIN
 */
class AsynchronousMethodHandler
    implements InvocationHandlerFactory.MethodHandler {
  private static final long MAX_RESPONSE_BUFFER_SIZE = 8192L;

  private final MethodMetadata metadata;
  private final Target<?> target;
  private final VertxHttpClient client;
  private final Retryer retryer;
  private final List<RequestInterceptor> requestInterceptors;
  private final Logger logger;
  private final Logger.Level logLevel;
  private final RequestTemplate.Factory buildTemplateFromArgs;
  private final HttpClientOptions options;
  private final Decoder decoder;
  private final ErrorDecoder errorDecoder;
  private final boolean decode404;

  private AsynchronousMethodHandler(
      Target<?> target,
      VertxHttpClient client,
      Retryer retryer,
      List<RequestInterceptor> requestInterceptors,
      Logger logger,
      Logger.Level logLevel,
      MethodMetadata metadata,
      RequestTemplate.Factory buildTemplateFromArgs,
      HttpClientOptions options,
      Decoder decoder,
      ErrorDecoder errorDecoder,
      boolean decode404) {
    this.target = checkNotNull(target, "target must be not null");
    this.client = checkNotNull(client, "client must be not null");
    this.retryer = checkNotNull(retryer,
        "retryer for %s must be not null", target);
    this.requestInterceptors = checkNotNull(requestInterceptors,
        "requestInterceptors for %s must be not null", target);
    this.logger = checkNotNull(logger,
        "logger for %s must be not null", target);
    this.logLevel = checkNotNull(logLevel,
        "logLevel for %s must be not null", target);
    this.metadata = checkNotNull(metadata,
        "metadata for %s must be not null", target);
    this.buildTemplateFromArgs = checkNotNull(buildTemplateFromArgs,
        "metadata for %s must be not null", target);
    this.options = checkNotNull(options,
        "options for %s must be not null", target);
    this.errorDecoder = checkNotNull(errorDecoder,
        "errorDecoder for %s must be not null", target);
    this.decoder = checkNotNull(decoder,
        "decoder for %s must be not null", target);
    this.decode404 = decode404;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Future invoke(final Object[] argv) throws Throwable {
    final RequestTemplate template = buildTemplateFromArgs.create(argv);
    final Retryer retryer = this.retryer.clone();

    final ResultHandlerWithRetryer handler =
        new ResultHandlerWithRetryer(template, retryer);
    executeAndDecode(template).setHandler(handler);

    return handler.getResultFuture();
  }

  /**
   * Executes request from {@code template} with {@code this.client} and
   * decodes the response. Result or occurred error wrapped in returned Future.
   *
   * @param template request template
   *
   * @return future with decoded result or occurred error
   */
  private Future<Object> executeAndDecode(final RequestTemplate template) {
    final Request request = targetRequest(template);
    final Future<Object> decodedResultFuture = Future.future();

    logRequest(request);

    final Instant start = Instant.now();

    client.execute(request, this.options).setHandler(res -> {
      boolean shouldClose = true;

      final long elapsedTime = Duration.between(start, Instant.now())
          .toMillis();

      if (res.succeeded()) {

        /* Just as executeAndDecode in SynchronousMethodHandler but wrapped
         * in Future */
        Response response = res.result();

        try {
          // TODO: check why this buffering is needed
          if (logLevel != Logger.Level.NONE) {
            response = logger.logAndRebufferResponse(metadata.configKey(),
                logLevel, response, elapsedTime);
          }

          if (Response.class == metadata.returnType()) {
            if (response.body() == null) {
              decodedResultFuture.complete(response);
            } else if (response.body().length() == null
                || response.body().length() > MAX_RESPONSE_BUFFER_SIZE) {
              shouldClose = false;
              decodedResultFuture.complete(response);
            } else {
              final byte[] bodyData = Util.toByteArray(
                  response.body().asInputStream());
              decodedResultFuture.complete(Response.create(
                  response.status(),
                  response.reason(),
                  response.headers(),
                  bodyData));
            }
          } else if (response.status() >= 200 && response.status() < 300) {
            if (Void.class == metadata.returnType()) {
              decodedResultFuture.complete();
            } else {
              decodedResultFuture.complete(decode(response));
            }
          } else if (decode404 && response.status() == 404) {
            decodedResultFuture.complete(
                decoder.decode(response, metadata.returnType()));
          } else {
            decodedResultFuture.fail(
                errorDecoder.decode(metadata.configKey(), response));
          }
        } catch (IOException ioException) {
          logIoException(ioException, elapsedTime);
          decodedResultFuture.fail(errorReading(request, response, ioException));
        } catch (FeignException exception) {
          decodedResultFuture.fail(exception);
        } finally {
          if (shouldClose) {
            ensureClosed(response.body());
          }
        }
      } else {
        if (res.cause() instanceof IOException) {
          logIoException((IOException) res.cause(), elapsedTime);
          decodedResultFuture.fail(errorExecuting(
              request, (IOException) res.cause()));
        } else {
          decodedResultFuture.fail(res.cause());
        }
      }
    });

    return decodedResultFuture;
  }

  /**
   * Associates request to defined target.
   *
   * @param template request template
   *
   * @return fully formed request
   */
  private Request targetRequest(final RequestTemplate template) {
    for (RequestInterceptor interceptor : requestInterceptors) {
      interceptor.apply(template);
    }
    return target.apply(new RequestTemplate(template));
  }

  /**
   * Transforms HTTP response body into object using decoder.
   *
   * @param response HTTP response
   *
   * @return decoded result
   *
   * @throws IOException IO exception during the reading of InputStream of
   *      response
   * @throws DecodeException when decoding failed due to a checked or unchecked
   *      exception besides IOException
   * @throws FeignException when decoding succeeds, but conveys the operation
   *      failed
   */
  private Object decode(final Response response) throws IOException,
      FeignException {
    try {
      return decoder.decode(response, metadata.returnType());
    } catch (FeignException feignException) {
      /* All feign exception including decode exceptions */
      throw feignException;
    } catch (RuntimeException unexpectedException) {
      /* Any unexpected exception */
      throw new DecodeException(
          unexpectedException.getMessage(), unexpectedException);
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
  private void logIoException(final IOException exception,
      final long elapsedTime) {
    if (logLevel != Logger.Level.NONE) {
      logger.logIOException(metadata.configKey(), logLevel, exception,
          elapsedTime);
    }
  }

  /**
   * Logs retry.
   */
  private void logRetry() {
    if (logLevel != Logger.Level.NONE) {
      logger.logRetry(metadata.configKey(), logLevel);
    }
  }

  static class Factory {
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
      this.client = checkNotNull(client, "client must not be null");
      this.retryer = checkNotNull(retryer, "retryer must not be null");
      this.requestInterceptors = checkNotNull(requestInterceptors,
          "requestInterceptors must not be null");
      this.logger = checkNotNull(logger, "logger must not be null");
      this.logLevel = checkNotNull(logLevel, "logLevel must not be null");
      this.decode404 = decode404;
    }

    MethodHandler create(
        final Target<?> target,
        final MethodMetadata metadata,
        final RequestTemplate.Factory buildTemplateFromArgs,
        final HttpClientOptions options,
        final Decoder decoder,
        final ErrorDecoder errorDecoder) {
      return new AsynchronousMethodHandler(
          target,
          client,
          retryer,
          requestInterceptors,
          logger,
          logLevel,
          metadata,
          buildTemplateFromArgs,
          options,
          decoder,
          errorDecoder,
          decode404);
    }
  }

  /**
   * Handler for {@link AsyncResult} able to retry execution of request. In this
   * case handler passed to new request.
   *
   * @param <T> type of response
   */
  private class ResultHandlerWithRetryer<T> implements Handler<AsyncResult<T>> {
    private final RequestTemplate template;
    private final Retryer retryer;
    private final Future<T> resultFuture = Future.future();

    private ResultHandlerWithRetryer(final RequestTemplate template,
        final Retryer retryer) {
      this.template = template;
      this.retryer = retryer;
    }

    /**
     * In case of failure retries HTTP request passing itself as handler.
     *
     * @param result result of asynchronous HTTP request execution
     */
    @Override
    @SuppressWarnings("unchecked")
    public void handle(AsyncResult<T> result) {
      if (result.succeeded()) {
        this.resultFuture.complete(result.result());
      } else {
        try {
          throw result.cause();
        } catch (RetryableException retryableException) {
          try {
            this.retryer.continueOrPropagate(retryableException);
            logRetry();
            ((Future<T>) executeAndDecode(this.template)).setHandler(this);
          } catch (RetryableException noMoreRetryAttempts) {
            this.resultFuture.fail(noMoreRetryAttempts);
          }
        } catch (Throwable otherException) {
          this.resultFuture.fail(otherException);
        }
      }
    }

    /**
     * @return future that will be completed after successful execution or after
     *      all attempts finished by fail.
     */
    private Future<?> getResultFuture() {
      return this.resultFuture;
    }
  }
}
