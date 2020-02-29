/**
 * Copyright 2012-2020 The Feign Authors
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

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Supplier;
import feign.Logger.NoOpLogger;
import feign.Request.Options;
import feign.Target.HardCodedTarget;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;

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
 *
 */
@Experimental
public abstract class AsyncFeign<C> extends Feign {

  public static <C> AsyncBuilder<C> asyncBuilder() {
    return new AsyncBuilder<>();
  }

  private static class LazyInitializedExecutorService {

    private static final ExecutorService instance = Executors.newCachedThreadPool(r -> {
      final Thread result = new Thread(r);
      result.setDaemon(true);
      return result;
    });
  }

  public static class AsyncBuilder<C> {

    private final Builder builder;
    private Supplier<C> defaultContextSupplier = () -> null;
    private AsyncClient<C> client;

    private final Logger.Level logLevel = Logger.Level.NONE;
    private final Logger logger = new NoOpLogger();

    private Decoder decoder = new Decoder.Default();
    private ErrorDecoder errorDecoder = new ErrorDecoder.Default();
    private boolean decode404;
    private boolean closeAfterDecode = true;

    public AsyncBuilder() {
      super();
      this.builder = Feign.builder();
    }

    public AsyncBuilder<C> defaultContextSupplier(Supplier<C> supplier) {
      this.defaultContextSupplier = supplier;
      return this;
    }

    public AsyncBuilder<C> client(AsyncClient<C> client) {
      this.client = client;
      return this;
    }

    /**
     * @see Builder#mapAndDecode(ResponseMapper, Decoder)
     */
    public AsyncBuilder<C> mapAndDecode(ResponseMapper mapper, Decoder decoder) {
      this.decoder = (response, type) -> decoder.decode(mapper.map(response, type), type);
      return this;
    }

    /**
     * @see Builder#decoder(Decoder)
     */
    public AsyncBuilder<C> decoder(Decoder decoder) {
      this.decoder = decoder;
      return this;
    }

    /**
     * @see Builder#decode404()
     */
    public AsyncBuilder<C> decode404() {
      this.decode404 = true;
      return this;
    }

    /**
     * @see Builder#errorDecoder(ErrorDecoder)
     */
    public AsyncBuilder<C> errorDecoder(ErrorDecoder errorDecoder) {
      this.errorDecoder = errorDecoder;
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

    private AsyncBuilder<C> lazyInits() {
      if (client == null) {
        client = new AsyncClient.Default<>(new Client.Default(null, null),
            LazyInitializedExecutorService.instance);
      }

      return this;
    }

    public AsyncFeign<C> build() {
      return new ReflectiveAsyncFeign<>(lazyInits());
    }

    // start of builder delgates

    /**
     * @see Builder#logLevel(Logger.Level)
     */
    public AsyncBuilder<C> logLevel(Logger.Level logLevel) {
      builder.logLevel(logLevel);
      return this;
    }

    /**
     * @see Builder#contract(Contract)
     */
    public AsyncBuilder<C> contract(Contract contract) {
      builder.contract(contract);
      return this;
    }

    /**
     * @see Builder#logLevel(Logger.Level)
     */
    public AsyncBuilder<C> logger(Logger logger) {
      builder.logger(logger);
      return this;
    }

    /**
     * @see Builder#encoder(Encoder)
     */
    public AsyncBuilder<C> encoder(Encoder encoder) {
      builder.encoder(encoder);
      return this;
    }

    /**
     * @see Builder#queryMapEncoder(QueryMapEncoder)
     */
    public AsyncBuilder<C> queryMapEncoder(QueryMapEncoder queryMapEncoder) {
      builder.queryMapEncoder(queryMapEncoder);
      return this;
    }


    /**
     * @see Builder#options(Options)
     */
    public AsyncBuilder<C> options(Options options) {
      builder.options(options);
      return this;
    }

    /**
     * @see Builder#requestInterceptor(RequestInterceptor)
     */
    public AsyncBuilder<C> requestInterceptor(RequestInterceptor requestInterceptor) {
      builder.requestInterceptor(requestInterceptor);
      return this;
    }

    /**
     * @see Builder#requestInterceptors(Iterable)
     */
    public AsyncBuilder<C> requestInterceptors(Iterable<RequestInterceptor> requestInterceptors) {
      builder.requestInterceptors(requestInterceptors);
      return this;
    }

    /**
     * @see Builder#invocationHandlerFactory(InvocationHandlerFactory)
     */
    public AsyncBuilder<C> invocationHandlerFactory(InvocationHandlerFactory invocationHandlerFactory) {
      builder.invocationHandlerFactory(invocationHandlerFactory);
      return this;
    }
  }

  private final ThreadLocal<AsyncInvocation<C>> activeContext;

  private final Feign feign;

  private final Supplier<C> defaultContextSupplier;
  private final AsyncClient<C> client;

  private final Logger.Level logLevel;
  private final Logger logger;

  private final AsyncResponseHandler responseHandler;

  protected AsyncFeign(AsyncBuilder<C> asyncBuilder) {
    this.activeContext = new ThreadLocal<>();

    this.defaultContextSupplier = asyncBuilder.defaultContextSupplier;
    this.client = asyncBuilder.client;

    this.logLevel = asyncBuilder.logLevel;
    this.logger = asyncBuilder.logger;

    this.responseHandler = new AsyncResponseHandler(
        asyncBuilder.logLevel,
        asyncBuilder.logger,
        asyncBuilder.decoder,
        asyncBuilder.errorDecoder,
        asyncBuilder.decode404,
        asyncBuilder.closeAfterDecode);

    asyncBuilder.builder.client(this::stageExecution);
    asyncBuilder.builder.decoder(this::stageDecode);
    asyncBuilder.builder.forceDecoding(); // force all handling through stageDecode

    this.feign = asyncBuilder.builder.build();
  }

  private Response stageExecution(Request request, Options options) {
    final Response result = Response.builder()
        .status(200)
        .request(request)
        .build();

    final AsyncInvocation<C> invocationContext = activeContext.get();

    invocationContext.setResponseFuture(
        client.execute(request, options, Optional.ofNullable(invocationContext.context())));


    return result;
  }

  // from SynchronousMethodHandler
  long elapsedTime(long start) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
  }


  private Object stageDecode(Response response, Type type) {
    final AsyncInvocation<C> invocationContext = activeContext.get();

    final CompletableFuture<Object> result = new CompletableFuture<>();

    invocationContext.responseFuture().whenComplete((r, t) -> {
      final long elapsedTime = elapsedTime(invocationContext.startNanos());

      if (t != null) {
        if (logLevel != Logger.Level.NONE && t instanceof IOException) {
          final IOException e = (IOException) t;
          logger.logIOException(invocationContext.configKey(), logLevel, e, elapsedTime);
        }
        result.completeExceptionally(t);
      } else {
        responseHandler.handleResponse(result, invocationContext.configKey(), r,
            invocationContext.underlyingType(), elapsedTime);
      }
    });

    result.whenComplete((r, t) -> {
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
  }


  protected void setInvocationContext(AsyncInvocation<C> invocationContext) {
    activeContext.set(invocationContext);
  }

  protected void clearInvocationContext() {
    activeContext.remove();
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
