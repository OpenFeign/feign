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
package feign.reactive;

import feign.InvocationHandlerFactory.MethodHandler;
import feign.Target;
import java.lang.reflect.Method;
import java.util.Map;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

public class ReactorInvocationHandler extends ReactiveInvocationHandler {
  private final Scheduler scheduler;

  ReactorInvocationHandler(Target<?> target,
      Map<Method, MethodHandler> dispatch,
      Scheduler scheduler) {
    super(target, dispatch);
    this.scheduler = scheduler;
  }

  @Override
  protected Publisher invoke(Method method, MethodHandler methodHandler, Object[] arguments) {
    Publisher<?> invocation = this.invokeMethod(methodHandler, arguments);
    if (Flux.class.isAssignableFrom(method.getReturnType())) {
      return Flux.from(invocation).subscribeOn(scheduler);
    } else if (Mono.class.isAssignableFrom(method.getReturnType())) {
      return Mono.from(invocation).subscribeOn(scheduler);
    }
    throw new IllegalArgumentException(
        "Return type " + method.getReturnType().getName() + " is not supported");
  }
}
