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

import java.lang.reflect.*;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Experimental
public class ReflectiveAsyncFeign<C> extends AsyncFeign<C> {

  private class AsyncFeignInvocationHandler<T> implements InvocationHandler {

    private final Map<Method, MethodInfo> methodInfoLookup = new ConcurrentHashMap<>();

    private final Class<T> type;
    private final T instance;
    private final C context;

    AsyncFeignInvocationHandler(Class<T> type, T instance, C context) {
      this.type = type;
      this.instance = instance;
      this.context = context;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if ("equals".equals(method.getName()) && method.getParameterCount() == 1) {
        try {
          final Object otherHandler =
              args.length > 0 && args[0] != null ? Proxy.getInvocationHandler(args[0])
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

      final MethodInfo methodInfo =
          methodInfoLookup.computeIfAbsent(method, m -> new MethodInfo(type, m));

      setInvocationContext(new AsyncInvocation<C>(context, methodInfo));
      try {
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
      if (obj instanceof AsyncFeignInvocationHandler) {
        final AsyncFeignInvocationHandler<?> other = (AsyncFeignInvocationHandler<?>) obj;
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

  public ReflectiveAsyncFeign(AsyncBuilder<C> asyncBuilder) {
    super(asyncBuilder);
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
        throw new IllegalArgumentException("Method return type is not CompleteableFuture: "
            + getFullMethodName(type, retType, m));
      }

      final Type genRetType = m.getGenericReturnType();

      if (!ParameterizedType.class.isInstance(genRetType)) {
        throw new IllegalArgumentException("Method return type is not parameterized: "
            + getFullMethodName(type, genRetType, m));
      }

      if (WildcardType.class
          .isInstance(ParameterizedType.class.cast(genRetType).getActualTypeArguments()[0])) {
        throw new IllegalArgumentException(
            "Wildcards are not supported for return-type parameters: "
                + getFullMethodName(type, genRetType, m));
      }
    }

    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
        new AsyncFeignInvocationHandler<>(type, instance, context)));
  }
}
