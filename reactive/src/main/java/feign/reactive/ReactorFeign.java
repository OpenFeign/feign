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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

public class ReactorFeign extends ReactiveFeign {

  public static Builder builder() {
    return new Builder(Schedulers.boundedElastic());
  }

  public static Builder builder(Scheduler scheduler) {
    return new Builder(scheduler);
  }

  public static class Builder extends ReactiveFeign.Builder {

    private final Scheduler scheduler;

    Builder(Scheduler scheduler) {
      this.scheduler = scheduler;
    }

    @Override
    public Feign internalBuild() {
      super.invocationHandlerFactory(new ReactorInvocationHandlerFactory(scheduler));
      return super.internalBuild();
    }

    @Override
    public Builder invocationHandlerFactory(InvocationHandlerFactory invocationHandlerFactory) {
      throw new UnsupportedOperationException(
          "Invocation Handler Factory overrides are not supported.");
    }
  }

  private static class ReactorInvocationHandlerFactory implements InvocationHandlerFactory {
    private final Scheduler scheduler;

    private ReactorInvocationHandlerFactory(Scheduler scheduler) {
      this.scheduler = scheduler;
    }

    @Override
    public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
      return new ReactorInvocationHandler(target, dispatch, scheduler);
    }
  }
}
