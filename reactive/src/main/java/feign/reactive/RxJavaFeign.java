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

import feign.Feign;
import feign.InvocationHandlerFactory;
import feign.Target;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

public class RxJavaFeign extends ReactiveFeign {

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder extends ReactiveFeign.Builder {

    private Scheduler scheduler = Schedulers.trampoline();

    @Override
    public Feign internalBuild() {
      super.invocationHandlerFactory(new RxJavaInvocationHandlerFactory(scheduler));
      return super.internalBuild();
    }

    @Override
    public Builder invocationHandlerFactory(InvocationHandlerFactory invocationHandlerFactory) {
      throw new UnsupportedOperationException(
          "Invocation Handler Factory overrides are not supported.");
    }

    public Builder scheduleOn(Scheduler scheduler) {
      this.scheduler = scheduler;
      return this;
    }
  }

  private static class RxJavaInvocationHandlerFactory implements InvocationHandlerFactory {
    private final Scheduler scheduler;

    private RxJavaInvocationHandlerFactory(Scheduler scheduler) {
      this.scheduler = scheduler;
    }

    @Override
    public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
      return new RxJavaInvocationHandler(target, dispatch, scheduler);
    }
  }

}
