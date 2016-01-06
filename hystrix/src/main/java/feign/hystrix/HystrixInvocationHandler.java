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

import static feign.Util.checkNotNull;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import feign.InvocationHandlerFactory;
import feign.InvocationHandlerFactory.MethodHandler;
import feign.Target;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;
import rx.Observable;
import rx.Single;

final class HystrixInvocationHandler implements InvocationHandler {

  private final Target target;
  private final Map<Method, MethodHandler> dispatch;

  HystrixInvocationHandler(Target target, Map<Method, MethodHandler> dispatch) {
    this.target = checkNotNull(target, "target");
    this.dispatch = checkNotNull(dispatch, "dispatch");
  }

  @Override
  public Object invoke(final Object proxy, final Method method, final Object[] args)
      throws Throwable {
    String groupKey = this.target.name();
    String commandKey = method.getName();
    HystrixCommand.Setter setter =
        HystrixCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(groupKey))
            .andCommandKey(HystrixCommandKey.Factory.asKey(commandKey));

    HystrixCommand<Object> hystrixCommand =
        new HystrixCommand<Object>(setter) {
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
        };

    if (HystrixCommand.class.isAssignableFrom(method.getReturnType())) {
      return hystrixCommand;
    } else if (Observable.class.isAssignableFrom(method.getReturnType())) {
      // Create a cold Observable
      return hystrixCommand.toObservable();
    } else if (Single.class.isAssignableFrom(method.getReturnType())) {
      // Create a cold Observable as a Single
      return hystrixCommand.toObservable().toSingle();
    }
    return hystrixCommand.execute();
  }

  static final class Factory implements InvocationHandlerFactory {

    @Override
    public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
      return new HystrixInvocationHandler(target, dispatch);
    }
  }
}
