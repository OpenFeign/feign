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

import feign.Logger.Level;
import feign.Request.Options;
import feign.Target.HardCodedTarget;
import feign.codec.Decoder;
import feign.codec.Encoder;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
public abstract class AsyncFeign<C> extends Feign {

  public static <C> AsyncBuilder<C> asyncBuilder() {
    return new AsyncBuilder<>();
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

    private Supplier<C> defaultContextSupplier = () -> null;
    private AsyncClient<C> client = new AsyncClient.Default<>(
        new Client.Default(null, null), LazyInitializedExecutorService.instance);

    private boolean closeAfterDecode = true;

    public AsyncBuilder<C> defaultContextSupplier(Supplier<C> supplier) {
      this.defaultContextSupplier = supplier;
      return this;
    }

    public AsyncBuilder<C> client(AsyncClient<C> client) {
      this.client = client;
      return this;
    }

    public AsyncBuilder<C> doNotCloseAfterDecode() {
      this.closeAfterDecode = false;
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

    public AsyncFeign<C> build() {
      ThreadLocal<AsyncInvocation<C>> activeContextHolder = new ThreadLocal<>();
      Supplier<C> defaultContextSupplier =
          Capability.enrich(this.defaultContextSupplier, Supplier.class, capabilities);
      AsyncClient<C> client = Capability.enrich(this.client, AsyncClient.class, capabilities);

      Logger logger = Capability.enrich(this.logger, Logger.class, capabilities);

      Decoder decoder = Capability.enrich(this.decoder, Decoder.class, capabilities);

      AsyncResponseHandler responseHandler =
          Capability.enrich(
              new AsyncResponseHandler(
                  logLevel,
                  logger,
                  decoder,
                  errorDecoder,
                  dismiss404,
                  closeAfterDecode),
              AsyncResponseHandler.class,
              capabilities);
      List<RequestInterceptor> requestInterceptors =
          this.requestInterceptors.stream()
              .map(ri -> Capability.enrich(ri, RequestInterceptor.class, capabilities))
              .collect(Collectors.toList());

      Contract contract = Capability.enrich(this.contract, Contract.class, capabilities);
      Encoder encoder = Capability.enrich(this.encoder, Encoder.class, capabilities);
      QueryMapEncoder queryMapEncoder =
          Capability.enrich(this.queryMapEncoder, QueryMapEncoder.class, capabilities);
      Options options = Capability.enrich(this.options, Options.class, capabilities);
      InvocationHandlerFactory invocationHandlerFactory =
          Capability.enrich(this.invocationHandlerFactory, InvocationHandlerFactory.class,
              capabilities);

      return new ReflectiveAsyncFeign<>(Feign.builder()
          .logLevel(logLevel)
          .client(stageExecution(activeContextHolder, client))
          .decoder(stageDecode(activeContextHolder, logger, logLevel, responseHandler))
          .forceDecoding() // force all handling through stageDecode
          .contract(contract)
          .logger(logger)
          .encoder(encoder)
          .queryMapEncoder(queryMapEncoder)
          .options(options)
          .requestInterceptors(requestInterceptors)
          .invocationHandlerFactory(invocationHandlerFactory)
          .build(), defaultContextSupplier, activeContextHolder);
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
  private Supplier<C> defaultContextSupplier;

  protected AsyncFeign(Feign feign, Supplier<C> defaultContextSupplier) {
    this.feign = feign;
    this.defaultContextSupplier = defaultContextSupplier;
  }



  @Override
  public <T> T newInstance(Target<T> target) {
    return newInstance(target, defaultContextSupplier.get());
  }

  public <T> T newInstance(Target<T> target, C context) {
    return wrap(target.type(), feign.newInstance(target), context);
  }

  protected abstract <T> T wrap(Class<T> type, T instance, C context);
}
