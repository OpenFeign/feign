/**
 * Copyright 2012-2018 The Feign Authors
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
package feign.reactive;

import feign.FeignException;
import feign.InvocationHandlerFactory.MethodHandler;
import feign.Target;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.Callable;
import org.reactivestreams.Publisher;

public abstract class ReactiveInvocationHandler implements InvocationHandler {

  private final Target<?> target;
  private final Map<Method, MethodHandler> dispatch;

  public ReactiveInvocationHandler(Target<?> target,
      Map<Method, MethodHandler> dispatch) {
    this.target = target;
    this.dispatch = dispatch;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
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
    return this.invoke(method, this.dispatch.get(method), args);
  }

  @Override
  public int hashCode() {
    return this.target.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (obj == this) {
      return true;
    }
    if (ReactiveInvocationHandler.class.isAssignableFrom(obj.getClass())) {
      return this.target.equals(obj);
    }
    return false;
  }

  @Override
  public String toString() {
    return "Target [" + this.target.toString() + "]";
  }

  /**
   * Invoke the Method Handler.
   *
   * @param method on the Target to invoke.
   * @param methodHandler to invoke
   * @param arguments for the method
   * @return a reactive {@link Publisher} for the invocation.
   */
  protected abstract Publisher invoke(Method method,
                                      MethodHandler methodHandler,
                                      Object[] arguments);

  /**
   * Invoke the Method Handler as a Callable.
   *
   * @param methodHandler to invoke
   * @param arguments for the method
   * @return a Callable wrapper for the invocation.
   */
  Callable<?> invokeMethod(MethodHandler methodHandler, Object[] arguments) {
    return () -> {
      try {
        return methodHandler.invoke(arguments);
      } catch (Throwable th) {
        if (th instanceof FeignException) {
          throw (FeignException) th;
        }
        throw new RuntimeException(th);
      }
    };
  }
}
