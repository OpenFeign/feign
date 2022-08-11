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
package feign.kotlin;

import feign.AsyncClient;
import feign.AsyncContextSupplier;
import feign.AsyncFeign;
import feign.AsyncInvocation;
import feign.AsyncJoinException;
import feign.AsyncResponseHandler;
import feign.BaseBuilder;
import feign.Capability;
import feign.Client;
import feign.Experimental;
import feign.Feign;
import feign.Logger;
import feign.Logger.Level;
import feign.MethodInfo;
import feign.Response;
import feign.Target;
import feign.Target.HardCodedTarget;
import feign.codec.Decoder;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.future.FutureKt;

@Experimental
public class CoroutineFeign<C> extends AsyncFeign<C> {
  public static <C> CoroutineBuilder<C> coBuilder() {
    return new CoroutineBuilder<>();
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

  private class CoroutineFeignInvocationHandler<T> implements InvocationHandler {

    private final Map<Method, MethodInfo> methodInfoLookup = new ConcurrentHashMap<>();

    private final Class<T> type;
    private final T instance;
    private final C context;

    CoroutineFeignInvocationHandler(Class<T> type, T instance, C context) {
      this.type = type;
      this.instance = instance;
      this.context = context;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if ("equals".equals(method.getName()) && method.getParameterCount() == 1) {
        try {
          final Object otherHandler =
              args.length > 0 && args[0] != null ? Proxy.getInvocationHandler(args[0]) : null;
          return equals(otherHandler);
        } catch (final IllegalArgumentException e) {
          return false;
        }
      } else if ("hashCode".equals(method.getName()) && method.getParameterCount() == 0) {
        return hashCode();
      } else if ("toString".equals(method.getName()) && method.getParameterCount() == 0) {
        return toString();
      }

      final MethodInfo methodInfo =
          methodInfoLookup.computeIfAbsent(method, m -> KotlinMethodInfo.createInstance(type, m));

      setInvocationContext(new AsyncInvocation<>(context, methodInfo));
      try {
        if (KotlinDetector.isSuspendingFunction(method)) {
          CompletableFuture<?> result = (CompletableFuture<?>) method.invoke(instance, args);
          Continuation<Object> continuation = (Continuation<Object>) args[args.length - 1];
          return FutureKt.await(result, continuation);
        }

        return method.invoke(instance, args);
      } catch (final InvocationTargetException e) {
        Throwable cause = e.getCause();
        if (cause instanceof AsyncJoinException) {
          cause = cause.getCause();
        }
        throw cause;
      } finally {
        clearInvocationContext();
      }
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object obj) {
      if (obj instanceof CoroutineFeignInvocationHandler) {
        final CoroutineFeignInvocationHandler<?> other = (CoroutineFeignInvocationHandler<?>) obj;
        return instance.equals(other.instance);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return instance.hashCode();
    }

    @Override
    public String toString() {
      return instance.toString();
    }
  }

  public static class CoroutineBuilder<C> extends BaseBuilder<CoroutineBuilder<C>> {

    private AsyncContextSupplier<C> defaultContextSupplier = () -> null;
    private AsyncClient<C> client =
        new AsyncClient.Default<>(
            new Client.Default(null, null), LazyInitializedExecutorService.instance);

    @Deprecated
    public CoroutineBuilder<C> defaultContextSupplier(Supplier<C> supplier) {
      this.defaultContextSupplier = supplier::get;
      return this;
    }

    public CoroutineBuilder<C> client(AsyncClient<C> client) {
      this.client = client;
      return this;
    }

    public CoroutineBuilder<C> defaultContextSupplier(AsyncContextSupplier<C> supplier) {
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

    public CoroutineFeign<C> build() {
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
                  closeAfterDecode,
                  responseInterceptor),
              AsyncResponseHandler.class,
              capabilities);

      return new CoroutineFeign<>(
          Feign.builder()
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
              .responseInterceptor(responseInterceptor)
              .invocationHandlerFactory(invocationHandlerFactory)
              .build(),
          defaultContextSupplier,
          activeContextHolder);
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
                      logger.logIOException(
                          invocationContext.configKey(), logLevel, e, elapsedTime);
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

  protected ThreadLocal<AsyncInvocation<C>> activeContextHolder;

  protected CoroutineFeign(
      Feign feign,
      AsyncContextSupplier<C> defaultContextSupplier,
      ThreadLocal<AsyncInvocation<C>> contextHolder) {
    super(feign, defaultContextSupplier);
    this.activeContextHolder = contextHolder;
  }

  protected void setInvocationContext(AsyncInvocation<C> invocationContext) {
    activeContextHolder.set(invocationContext);
  }

  protected void clearInvocationContext() {
    activeContextHolder.remove();
  }

  private String getFullMethodName(Class<?> type, Type retType, Method m) {
    return retType.getTypeName() + " " + type.toGenericString() + "." + m.getName();
  }

  @Override
  protected <T> T wrap(Class<T> type, T instance, C context) {
    if (!type.isInterface()) {
      throw new IllegalArgumentException("Type must be an interface: " + type);
    }

    for (final Method m : type.getMethods()) {
      final Class<?> retType = m.getReturnType();

      if (!CompletableFuture.class.isAssignableFrom(retType)) {
        continue; // synchronous case
      }

      if (retType != CompletableFuture.class) {
        throw new IllegalArgumentException(
            "Method return type is not CompleteableFuture: " + getFullMethodName(type, retType, m));
      }

      final Type genRetType = m.getGenericReturnType();

      if (!ParameterizedType.class.isInstance(genRetType)) {
        throw new IllegalArgumentException(
            "Method return type is not parameterized: " + getFullMethodName(type, genRetType, m));
      }

      if (WildcardType.class.isInstance(
          ParameterizedType.class.cast(genRetType).getActualTypeArguments()[0])) {
        throw new IllegalArgumentException(
            "Wildcards are not supported for return-type parameters: "
                + getFullMethodName(type, genRetType, m));
      }
    }

    return type.cast(
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            new CoroutineFeignInvocationHandler<>(type, instance, context)));
  }
}
