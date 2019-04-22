/**
 * Copyright 2012-2019 The Feign Authors
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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;
import feign.*;
import feign.FeignConfig.FeignConfigBuilder;

public class ReactorFeign extends ReactiveFeign {

  public static Builder builder() {
    return new Builder(FeignConfig.builder());
  }

  public static class Builder extends ReactiveFeign.Builder {

    protected Builder(FeignConfigBuilder feignConfigBuilder) {
      super(feignConfigBuilder);
    }

    @Override
    public Feign build() {
      super.invocationHandlerFactory(new ReactorInvocationHandlerFactory());
      return super.build();
    }

    @Override
    public Feign.Builder invocationHandlerFactory(
                                                  InvocationHandlerFactory invocationHandlerFactory) {
      throw new UnsupportedOperationException(
          "Invocation Handler Factory overrides are not supported.");
    }
  }

  private static class ReactorInvocationHandlerFactory implements InvocationHandlerFactory {
    @Override
    public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
      return new ReactorInvocationHandler(target, dispatch);
    }
  }
}
