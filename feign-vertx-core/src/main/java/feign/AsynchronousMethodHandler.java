package feign;

import static feign.FeignException.errorExecuting;
import static feign.FeignException.errorReading;
import static feign.Util.ensureClosed;

import feign.InvocationHandlerFactory.MethodHandler;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import feign.vertx.VertxAdaptors;
import feign.vertx.adaptor.AbstractVertxHttpClient;
import feign.vertx.adaptor.VertxFuture;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * Method handler for asynchronous HTTP requests via {@link AbstractVertxHttpClient}.
 * Inspired by {@link SynchronousMethodHandler}.
 *
 * @author Alexei KLENIN
 * @author Gordon McKinney
 */
final class AsynchronousMethodHandler implements MethodHandler {
  private static final long MAX_RESPONSE_BUFFER_SIZE = 8192L;

  private final MethodMetadata metadata;
  private final Target<?> target;
  private final AbstractVertxHttpClient<?, ?, Object> client;
  private final Retryer retryer;
  private final List<RequestInterceptor> requestInterceptors;
  private final Logger logger;
  private final Logger.Level logLevel;
  private final RequestTemplate.Factory buildTemplateFromArgs;
  private final Decoder decoder;
  private final ErrorDecoder errorDecoder;
  private final boolean decode404;

  private AsynchronousMethodHandler(
      final Target<?> target,
      final AbstractVertxHttpClient<?, ?, Object> client,
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
  public Object invoke(final Object[] argv) {
    final RequestTemplate template = buildTemplateFromArgs.create(argv);
    final Retryer retryer = this.retryer.clone();

    final ResultHandlerWithRetryer handler = new ResultHandlerWithRetryer(template, retryer);
    executeAndDecode(template).setHandler(handler);

    return handler.getResultFuture();
  }

  /**
   * Executes request from {@code template} with {@code this.client} and decodes the response.
   * Result or occurred error wrapped in returned Future.
   *
   * @param template  request template
   *
   * @return future with decoded result or occurred error
   */
  private VertxFuture<?, Object> executeAndDecode(final RequestTemplate template) {
    final Request request = targetRequest(template);
    final VertxFuture<Object, Object> decodedResultFuture = VertxAdaptors.getAdaptor().future();

    logRequest(request);

    final Instant start = Instant.now();

    client.execute(request).setHandler((VertxFuture<Object, Response> res) -> {
      boolean shouldClose = true;

      final long elapsedTime = Duration.between(start, Instant.now()).toMillis();

      if (res.succeeded()) {

        /* Just as executeAndDecode in SynchronousMethodHandler but wrapped in Future */
        Response response = res.result();

        try {
          // TODO: check why this buffering is needed
          if (logLevel != Logger.Level.NONE) {
            response = logger.logAndRebufferResponse(
                metadata.configKey(),
                logLevel,
                response,
                elapsedTime);
          }

          if (Response.class == metadata.returnType()) {
            if (response.body() == null) {
              decodedResultFuture.complete(response);
            } else if (response.body().length() == null
                || response.body().length() > MAX_RESPONSE_BUFFER_SIZE) {
              shouldClose = false;
              decodedResultFuture.complete(response);
            } else {
              final byte[] bodyData = Util.toByteArray(response.body().asInputStream());
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
            decodedResultFuture.complete(decoder.decode(response, metadata.returnType()));
          } else {
            decodedResultFuture.fail(errorDecoder.decode(metadata.configKey(), response));
          }
        } catch (final IOException ioException) {
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
          decodedResultFuture.fail(errorExecuting(request, (IOException) res.cause()));
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
   * @param template  request template
   *
   * @return fully formed request
   */
  private Request targetRequest(final RequestTemplate template) {
    for (final RequestInterceptor interceptor : requestInterceptors) {
      interceptor.apply(template);
    }

    return target.apply(new RequestTemplate(template));
  }

  /**
   * Transforms HTTP response body into object using decoder.
   *
   * @param response  HTTP response
   *
   * @return decoded result
   *
   * @throws IOException IO exception during the reading of InputStream of response
   * @throws DecodeException when decoding failed due to a checked or unchecked exception besides
   *     IOException
   * @throws FeignException when decoding succeeds, but conveys the operation failed
   */
  private Object decode(final Response response) throws IOException, FeignException {
    try {
      return decoder.decode(response, metadata.returnType());
    } catch (final FeignException feignException) {
      /* All feign exception including decode exceptions */
      throw feignException;
    } catch (final RuntimeException unexpectedException) {
      /* Any unexpected exception */
      throw new DecodeException(unexpectedException.getMessage(), unexpectedException);
    }
  }

  /**
   * Logs request.
   *
   * @param request  HTTP request
   */
  private void logRequest(final Request request) {
    if (logLevel != Logger.Level.NONE) {
      logger.logRequest(metadata.configKey(), logLevel, request);
    }
  }

  /**
   * Logs IO exception.
   *
   * @param exception  IO exception
   * @param elapsedTime  time spent to execute request
   */
  private void logIoException(final IOException exception, final long elapsedTime) {
    if (logLevel != Logger.Level.NONE) {
      logger.logIOException(metadata.configKey(), logLevel, exception, elapsedTime);
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

  static final class Factory {
    private final AbstractVertxHttpClient client;
    private final Retryer retryer;
    private final List<RequestInterceptor> requestInterceptors;
    private final Logger logger;
    private final Logger.Level logLevel;
    private final boolean decode404;

    Factory(
        final AbstractVertxHttpClient client,
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
      return new AsynchronousMethodHandler(
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
   * Handler for {@link VertxFuture} able to retry execution of request. In this case handler passed
   * to new request.
   *
   * @param <T>  type of response
   */
  private final class ResultHandlerWithRetryer<T> implements Consumer<VertxFuture<Object, T>> {
    private final RequestTemplate template;
    private final Retryer retryer;
    private final VertxFuture<?, T> resultFuture = (VertxFuture<?, T>) VertxAdaptors.getAdaptor().future();

    private ResultHandlerWithRetryer(final RequestTemplate template, final Retryer retryer) {
      this.template = template;
      this.retryer = retryer;
    }

    /**
     * In case of failure retries HTTP request passing itself as handler.
     *
     * @param result  result of asynchronous HTTP request execution
     */
    @Override
    @SuppressWarnings("unchecked")
    public void accept(VertxFuture<Object, T> result) {
      if (result.succeeded()) {
        this.resultFuture.complete(result.result());
      } else {
        try {
          throw result.cause();
        } catch (final RetryableException retryableException) {
          try {
            this.retryer.continueOrPropagate(retryableException);
            logRetry();
            ((VertxFuture<Object, T>) executeAndDecode(this.template)).setHandler(this);
          } catch (final RetryableException noMoreRetryAttempts) {
            this.resultFuture.fail(noMoreRetryAttempts);
          }
        } catch (final Throwable otherException) {
          this.resultFuture.fail(otherException);
        }
      }
    }

    /**
     * Returns a future that will be completed after successful execution or after all attempts
     * finished by fail.
     *
     * @return future with result of attempts
     */
    private Object getResultFuture() {
      return this.resultFuture.asFuture();
    }
  }
}
