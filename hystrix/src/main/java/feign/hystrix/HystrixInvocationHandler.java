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
import java.util.Map;

import feign.InvocationHandlerFactory;
import feign.InvocationHandlerFactory.MethodHandler;
import feign.Target;
import rx.Observable;
import rx.Single;
import rx.functions.Action1;

import static feign.Util.checkNotNull;

final class HystrixInvocationHandler implements InvocationHandler {

  private final Target<?> target;
  private final Map<Method, MethodHandler> dispatch;
  private final Object fallback; // Nullable

  HystrixInvocationHandler(Target<?> target, Map<Method, MethodHandler> dispatch, Object fallback) {
    this.target = checkNotNull(target, "target");
    this.dispatch = checkNotNull(dispatch, "dispatch");
    this.fallback = fallback;
  }

  @Override
  public Object invoke(final Object proxy, final Method method, final Object[] args)
      throws Throwable {
    String groupKey = this.groupKey();
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
          Object result = method.invoke(fallback, args);
          if (isReturnsHystrixCommand(method)) {
            return ((HystrixCommand) result).execute();
          } else if (isReturnsObservable(method)) {
            // Create a cold Observable
            return ((Observable) result).toBlocking().first();
          } else if (isReturnsSingle(method)) {
            // Create a cold Observable as a Single
            return ((Single) result).toObservable().toBlocking().first();
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
    }
    return hystrixCommand.execute();
  }

  private String groupKey() {
    if (this.target.type().isAnnotationPresent(HystrixGroupKey.class)) {
      final String key = this.target.type().getAnnotation(HystrixGroupKey.class).value();
      if (!key.isEmpty()) {
        return key;
      }
    }
    return this.target.name();
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

  static final class Factory implements InvocationHandlerFactory {

    @Override
    public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
      return new HystrixInvocationHandler(target, dispatch, null);
    }
  }
}
