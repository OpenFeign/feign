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

import feign.InvocationHandlerFactory.MethodHandler;
import feign.Target;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.Callable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ReactorInvocationHandler extends ReactiveInvocationHandler {

  ReactorInvocationHandler(Target<?> target,
      Map<Method, MethodHandler> dispatch) {
    super(target, dispatch);
  }

  @Override
  protected Publisher invoke(Method method, MethodHandler methodHandler, Object[] arguments) {
    Callable<?> invocation = this.invokeMethod(methodHandler, arguments);
    if (Flux.class.isAssignableFrom(method.getReturnType())) {
      return Flux.from(Mono.fromCallable(invocation)).subscribeOn(Schedulers.elastic());
    } else if (Mono.class.isAssignableFrom(method.getReturnType())) {
      return Mono.fromCallable(invocation).subscribeOn(Schedulers.elastic());
    }
    throw new IllegalArgumentException(
        "Return type " + method.getReturnType().getName() + " is not supported");
  }
}
