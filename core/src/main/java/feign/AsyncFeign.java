/*
 * Copyright 2012-2022 The Feign Authors
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

import feign.InvocationHandlerFactory.MethodHandler;
import feign.ReflectiveFeign.ParseHandlersByName;
import feign.Logger.Level;
import feign.Request.Options;
import feign.Target.HardCodedTarget;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Enhances {@link Feign} to provide support for asynchronous clients. Context (for example for
 * session cookies or tokens) is explicit, as calls for the same session may be done across several
 * threads. <br>
 * <br>
 * {@link Retryer} is not supported in this model, as that is a blocking API.
 * {@link ExceptionPropagationPolicy} is made redundant as {@link RetryableException} is never
 * thrown. <br>
 * Alternative approaches to retrying can be handled through {@link AsyncClient clients}. <br>
 * <br>
 * Target interface methods must return {@link CompletableFuture} with a non-wildcard type. As the
 * completion is done by the {@link AsyncClient}, it is important that any subsequent processing on
 * the thread be short - generally, this should involve notifying some other thread of the work to
 * be done (for example, creating and submitting a task to an {@link ExecutorService}).
 */
@Experimental
public abstract class AsyncFeign<C> {
  public static <C> AsyncBuilder<C> builder() {
    return new AsyncBuilder<>();
  }

  /**
   * @deprecated use {@link #builder()} instead.
   */
  @Deprecated()
  public static <C> AsyncBuilder<C> asyncBuilder() {
    return builder();
  }

  private static class LazyInitializedExecutorService {

    private static final ExecutorService instance =
        Executors.newCachedThreadPool(
            r -> {
              final Thread result = new Thread(r);
              result.setDaemon(true);
              return result;
            });
  }

  public static class AsyncBuilder<C> extends BaseBuilder<AsyncBuilder<C>> {

    private AsyncContextSupplier<C> defaultContextSupplier = () -> null;
    private AsyncClient<C> client = new AsyncClient.Default<>(
        new Client.Default(null, null), LazyInitializedExecutorService.instance);
    private MethodInfoResolver methodInfoResolver = MethodInfo::new;

    @Deprecated
    public AsyncBuilder<C> defaultContextSupplier(Supplier<C> supplier) {
      this.defaultContextSupplier = supplier::get;
      return this;
    }

    public AsyncBuilder<C> client(AsyncClient<C> client) {
      this.client = client;
      return this;
    }

    public AsyncBuilder<C> methodInfoResolver(MethodInfoResolver methodInfoResolver) {
      this.methodInfoResolver = methodInfoResolver;
      return this;
    }

    @Override
    public AsyncBuilder<C> mapAndDecode(ResponseMapper mapper, Decoder decoder) {
      return super.mapAndDecode(mapper, decoder);
    }

    @Override
    public AsyncBuilder<C> decoder(Decoder decoder) {
      return super.decoder(decoder);
    }

    @Override
    @Deprecated
    public AsyncBuilder<C> decode404() {
      return super.decode404();
    }

    @Override
    public AsyncBuilder<C> dismiss404() {
      return super.dismiss404();
    }

    @Override
    public AsyncBuilder<C> errorDecoder(ErrorDecoder errorDecoder) {
      return super.errorDecoder(errorDecoder);
    }

    @Override
    public AsyncBuilder<C> doNotCloseAfterDecode() {
      return super.doNotCloseAfterDecode();
    }

    public AsyncBuilder<C> defaultContextSupplier(AsyncContextSupplier<C> supplier) {
      this.defaultContextSupplier = supplier;
      return this;
    }

    public <T> T target(Class<T> apiType, String url) {
      return target(new HardCodedTarget<>(apiType, url));
    }

    public <T> T target(Class<T> apiType, String url, C context) {
      return target(new HardCodedTarget<>(apiType, url), context);
    }

    public <T> T target(Target<T> target) {
      return build().newInstance(target);
    }

    public <T> T target(Target<T> target, C context) {
      return build().newInstance(target, context);
    }

    @Override
    public AsyncBuilder<C> logLevel(Level logLevel) {
      return super.logLevel(logLevel);
    }

    @Override
    public AsyncBuilder<C> contract(Contract contract) {
      return super.contract(contract);
    }

    @Override
    public AsyncBuilder<C> logger(Logger logger) {
      return super.logger(logger);
    }

    @Override
    public AsyncBuilder<C> encoder(Encoder encoder) {
      return super.encoder(encoder);
    }

    @Override
    public AsyncBuilder<C> queryMapEncoder(QueryMapEncoder queryMapEncoder) {
      return super.queryMapEncoder(queryMapEncoder);
    }

    @Override
    public AsyncBuilder<C> options(Options options) {
      return super.options(options);
    }

    @Override
    public AsyncBuilder<C> requestInterceptor(RequestInterceptor requestInterceptor) {
      return super.requestInterceptor(requestInterceptor);
    }

    @Override
    public AsyncBuilder<C> requestInterceptors(Iterable<RequestInterceptor> requestInterceptors) {
      return super.requestInterceptors(requestInterceptors);
    }

    @Override
    public AsyncBuilder<C> invocationHandlerFactory(InvocationHandlerFactory invocationHandlerFactory) {
      return super.invocationHandlerFactory(invocationHandlerFactory);
    }

    public AsyncFeign<C> build() {
      super.enrich();
      ThreadLocal<AsyncInvocation<C>> activeContextHolder = new ThreadLocal<>();

      AsyncResponseHandler responseHandler =
          (AsyncResponseHandler) Capability.enrich(
              new AsyncResponseHandler(
                  logLevel,
                  logger,
                  decoder,
                  errorDecoder,
                  dismiss404,
                  closeAfterDecode, responseInterceptor),
              AsyncResponseHandler.class,
              capabilities);

      final MethodHandler.Factory<C> methodHandlerFactory =
          new AsynchronousMethodHandler.Factory<>(stageExecution(activeContextHolder, client),
              retryer, requestInterceptors,
              responseInterceptor, logger, logLevel,
              propagationPolicy);
      final ParseHandlersByName<C> handlersByName =
          new ParseHandlersByName<>(contract, options, encoder,
              stageDecode(activeContextHolder, logger, logLevel, responseHandler), queryMapEncoder,
              errorDecoder, methodHandlerFactory);
      final ReflectiveFeign<C> feign =
          new ReflectiveFeign<>(handlersByName, invocationHandlerFactory, queryMapEncoder);
      return new ReflectiveAsyncFeign<>(feign, defaultContextSupplier, activeContextHolder,
          methodInfoResolver);
    }

    private Client stageExecution(
                                  ThreadLocal<AsyncInvocation<C>> activeContext,
                                  AsyncClient<C> client) {
      return (request, options) -> {
        final Response result = Response.builder().status(200).request(request).build();

        final AsyncInvocation<C> invocationContext = activeContext.get();

        invocationContext.setResponseFuture(
            client.execute(request, options, Optional.ofNullable(invocationContext.context())));

        return result;
      };
    }

    // from SynchronousMethodHandler
    long elapsedTime(long start) {
      return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private Decoder stageDecode(
                                ThreadLocal<AsyncInvocation<C>> activeContext,
                                Logger logger,
                                Level logLevel,
                                AsyncResponseHandler responseHandler) {
      return (response, type) -> {
        final AsyncInvocation<C> invocationContext = activeContext.get();

        final CompletableFuture<Object> result = new CompletableFuture<>();

        invocationContext
            .responseFuture()
            .whenComplete(
                (r, t) -> {
                  final long elapsedTime = elapsedTime(invocationContext.startNanos());

                  if (t != null) {
                    if (logLevel != Logger.Level.NONE && t instanceof IOException) {
                      final IOException e = (IOException) t;
                      logger.logIOException(invocationContext.configKey(), logLevel, e,
                          elapsedTime);
                    }
                    result.completeExceptionally(t);
                  } else {
                    responseHandler.handleResponse(
                        result,
                        invocationContext.configKey(),
                        r,
                        invocationContext.underlyingType(),
                        elapsedTime);
                  }
                });

        result.whenComplete(
            (r, t) -> {
              if (result.isCancelled()) {
                invocationContext.responseFuture().cancel(true);
              }
            });

        if (invocationContext.isAsyncReturnType()) {
          return result;
        }
        try {
          return result.join();
        } catch (final CompletionException e) {
          final Response r = invocationContext.responseFuture().join();
          Throwable cause = e.getCause();
          if (cause == null) {
            cause = e;
          }
          throw new AsyncJoinException(r.status(), cause.getMessage(), r.request(), cause);
        }
      };
    }
  }

  private final Feign feign;
  private AsyncContextSupplier<C> defaultContextSupplier;

  protected AsyncFeign(Feign feign, AsyncContextSupplier<C> defaultContextSupplier) {
    this.feign = feign;
    this.defaultContextSupplier = defaultContextSupplier;
  }

  public <T> T newInstance(Target<T> target) {
    return newInstance(target, defaultContextSupplier.newContext());
  }

  public <T> T newInstance(Target<T> target, C context) {
    return wrap(target.type(), feign.newInstance(target), context);
  }

  protected abstract <T> T wrap(Class<T> type, T instance, C context);
}
