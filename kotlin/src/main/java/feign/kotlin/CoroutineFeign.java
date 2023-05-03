/*
 * Copyright 2012-2023 The Feign Authors
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
import feign.BaseBuilder;
import feign.Client;
import feign.Experimental;
import feign.MethodInfoResolver;
import feign.Target;
import feign.Target.HardCodedTarget;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.future.FutureKt;

@Experimental
public class CoroutineFeign<C> {
  public static <C> CoroutineBuilder<C> builder() {
    return new CoroutineBuilder<>();
  }

  /**
   * @deprecated use {@link #builder()} instead.
   */
  @Deprecated()
  public static <C> CoroutineBuilder<C> coBuilder() {
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

  private static class CoroutineFeignInvocationHandler<T> implements InvocationHandler {

    private final T instance;

    CoroutineFeignInvocationHandler(T instance) {
      this.instance = instance;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if ("equals".equals(method.getName()) && method.getParameterCount() == 1) {
        try {
          final Object otherHandler = args.length > 0 && args[0] != null
              ? Proxy.getInvocationHandler(args[0])
              : null;
          return equals(otherHandler);
        } catch (final IllegalArgumentException e) {
          return false;
        }
      } else if ("hashCode".equals(method.getName()) && method.getParameterCount() == 0) {
        return hashCode();
      } else if ("toString".equals(method.getName()) && method.getParameterCount() == 0) {
        return toString();
      }

      if (MethodKt.isSuspend(method)) {
        CompletableFuture<?> result = (CompletableFuture<?>) method.invoke(instance, args);
        Continuation<Object> continuation = (Continuation<Object>) args[args.length - 1];
        return FutureKt.await(result, continuation);
      }

      return method.invoke(instance, args);
    }

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
    private AsyncClient<C> client = new AsyncClient.Default<>(
        new Client.Default(null, null), LazyInitializedExecutorService.instance);
    private MethodInfoResolver methodInfoResolver = KotlinMethodInfo::createInstance;

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

    public CoroutineBuilder<C> methodInfoResolver(MethodInfoResolver methodInfoResolver) {
      this.methodInfoResolver = methodInfoResolver;
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

    @SuppressWarnings("unchecked")
    public CoroutineFeign<C> build() {
      super.enrich();

      AsyncFeign<C> asyncFeign = (AsyncFeign<C>) AsyncFeign.builder()
          .logLevel(logLevel)
          .client((AsyncClient<Object>) client)
          .decoder(decoder)
          .errorDecoder(errorDecoder)
          .contract(contract)
          .retryer(retryer)
          .logger(logger)
          .encoder(encoder)
          .queryMapEncoder(queryMapEncoder)
          .options(options)
          .requestInterceptors(requestInterceptors)
          .responseInterceptor(responseInterceptor)
          .invocationHandlerFactory(invocationHandlerFactory)
          .defaultContextSupplier((AsyncContextSupplier<Object>) defaultContextSupplier)
          .methodInfoResolver(methodInfoResolver)
          .build();
      return new CoroutineFeign<>(asyncFeign);
    }
  }

  private final AsyncFeign<C> feign;

  protected CoroutineFeign(AsyncFeign<C> feign) {
    this.feign = feign;
  }

  public <T> T newInstance(Target<T> target) {
    T instance = feign.newInstance(target);
    return wrap(target.type(), instance);
  }

  public <T> T newInstance(Target<T> target, C context) {
    T instance = feign.newInstance(target, context);
    return wrap(target.type(), instance);
  }

  private <T> T wrap(Class<T> type, T instance) {
    return type.cast(
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            new CoroutineFeignInvocationHandler<>(instance)));
  }
}
