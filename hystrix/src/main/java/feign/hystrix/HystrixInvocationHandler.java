/*
 * Copyright 2015 Netflix, Inc.
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
package feign.hystrix;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

import feign.InvocationHandlerFactory;
import feign.InvocationHandlerFactory.MethodHandler;
import feign.Target;
import rx.Completable;
import rx.Observable;
import rx.Single;

import static feign.Util.checkNotNull;

final class HystrixInvocationHandler implements InvocationHandler {

  private final Target<?> target;
  private final Map<Method, MethodHandler> dispatch;
  private final Object fallback; // Nullable
  private final Map<Method, Method> fallbackMethodMap;

  HystrixInvocationHandler(Target<?> target, Map<Method, MethodHandler> dispatch, Object fallback) {
    this.target = checkNotNull(target, "target");
    this.dispatch = checkNotNull(dispatch, "dispatch");
    this.fallback = fallback;
    this.fallbackMethodMap = toFallbackMethod(dispatch);
  }

  /**
   * If the method param of InvocationHandler.invoke is not accessible, i.e in a package-private
   * interface, the fallback call in hystrix command will fail cause of access restrictions.
   * But methods in dispatch are copied methods. So setting access to dispatch method doesn't take
   * effect to the method in InvocationHandler.invoke. Use map to store a copy of method
   * to invoke the fallback to bypass this and reducing the count of reflection calls.
   * @return cached methods map for fallback invoking
   */
  private Map<Method, Method> toFallbackMethod(Map<Method, MethodHandler> dispatch) {
    Map<Method, Method> result = new LinkedHashMap<Method, Method>();
    for (Method method : dispatch.keySet()) {
      method.setAccessible(true);
      result.put(method, method);
    }
    return result;
  }

  @Override
  public Object invoke(final Object proxy, final Method method, final Object[] args)
      throws Throwable {
    // early exit if the invoked method is from java.lang.Object
    // code is the same as ReflectiveFeign.FeignInvocationHandler
    if ("equals".equals(method.getName())) {
      try {
        Object otherHandler =
            args.length > 0 && args[0] != null ? Proxy.getInvocationHandler(args[0]) : null;
        return equals(otherHandler);
      } catch (IllegalArgumentException e) {
        return false;
      }
    } else if ("hashCode".equals(method.getName())) {
      return hashCode();
    } else if ("toString".equals(method.getName())) {
      return toString();
    }

    String groupKey = this.target.name();
    String commandKey = method.getName();
    HystrixCommand.Setter setter = HystrixCommand.Setter
        .withGroupKey(HystrixCommandGroupKey.Factory.asKey(groupKey))
        .andCommandKey(HystrixCommandKey.Factory.asKey(commandKey));

    HystrixCommand<Object> hystrixCommand = new HystrixCommand<Object>(setter) {
      @Override
      protected Object run() throws Exception {
        try {
          return HystrixInvocationHandler.this.dispatch.get(method).invoke(args);
        } catch (Exception e) {
          throw e;
        } catch (Throwable t) {
          throw (Error) t;
        }
      }

      @Override
      protected Object getFallback() {
        if (fallback == null) {
          return super.getFallback();
        }
        try {
          Object result = fallbackMethodMap.get(method).invoke(fallback, args);
          if (isReturnsHystrixCommand(method)) {
            return ((HystrixCommand) result).execute();
          } else if (isReturnsObservable(method)) {
            // Create a cold Observable
            return ((Observable) result).toBlocking().first();
          } else if (isReturnsSingle(method)) {
            // Create a cold Observable as a Single
            return ((Single) result).toObservable().toBlocking().first();
          } else if (isReturnsCompletable(method)) {
            ((Completable) result).await();
            return null;
          } else {
            return result;
          }
        } catch (IllegalAccessException e) {
          // shouldn't happen as method is public due to being an interface
          throw new AssertionError(e);
        } catch (InvocationTargetException e) {
          // Exceptions on fallback are tossed by Hystrix
          throw new AssertionError(e.getCause());
        }
      }
    };

    if (isReturnsHystrixCommand(method)) {
      return hystrixCommand;
    } else if (isReturnsObservable(method)) {
      // Create a cold Observable
      return hystrixCommand.toObservable();
    } else if (isReturnsSingle(method)) {
      // Create a cold Observable as a Single
      return hystrixCommand.toObservable().toSingle();
    } else if(isReturnsCompletable(method)) {
      return hystrixCommand.toObservable().toCompletable();
    }
    return hystrixCommand.execute();
  }

  private boolean isReturnsCompletable(Method method) {
    return Completable.class.isAssignableFrom(method.getReturnType());
  }

  private boolean isReturnsHystrixCommand(Method method) {
    return HystrixCommand.class.isAssignableFrom(method.getReturnType());
  }

  private boolean isReturnsObservable(Method method) {
    return Observable.class.isAssignableFrom(method.getReturnType());
  }

  private boolean isReturnsSingle(Method method) {
    return Single.class.isAssignableFrom(method.getReturnType());
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof HystrixInvocationHandler) {
      HystrixInvocationHandler other = (HystrixInvocationHandler) obj;
      return target.equals(other.target);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return target.hashCode();
  }

  @Override
  public String toString() {
    return target.toString();
  }

  static final class Factory implements InvocationHandlerFactory {

    @Override
    public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
      return new HystrixInvocationHandler(target, dispatch, null);
    }
  }
}
