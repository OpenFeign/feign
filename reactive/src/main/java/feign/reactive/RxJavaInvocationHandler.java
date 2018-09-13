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
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import java.lang.reflect.Method;
import java.util.Map;
import org.reactivestreams.Publisher;

public class RxJavaInvocationHandler extends ReactiveInvocationHandler {

  RxJavaInvocationHandler(Target<?> target,
      Map<Method, MethodHandler> dispatch) {
    super(target, dispatch);
  }

  @Override
  protected Publisher invoke(Method method, MethodHandler methodHandler, Object[] arguments) {
    return Flowable.fromCallable(this.invokeMethod(methodHandler, arguments))
        .observeOn(Schedulers.trampoline());
  }
}
