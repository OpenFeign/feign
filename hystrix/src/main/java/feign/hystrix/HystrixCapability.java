/**
 * Copyright 2012-2020 The Feign Authors
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
package feign.hystrix;

import com.netflix.hystrix.HystrixCommand;
import java.util.HashMap;
import java.util.Map;
import feign.Capability;
import feign.Contract;
import feign.InvocationHandlerFactory;

/**
 * Allows Feign interfaces to return HystrixCommand or rx.Observable or rx.Single objects. Also
 * decorates normal Feign methods with circuit breakers, but calls {@link HystrixCommand#execute()}
 * directly.
 */
public final class HystrixCapability implements Capability {

  private SetterFactory setterFactory = new SetterFactory.Default();
  private final Map<Class, Object> fallbacks = new HashMap<>();

  /**
   * Allows you to override hystrix properties such as thread pools and command keys.
   */
  public HystrixCapability setterFactory(SetterFactory setterFactory) {
    this.setterFactory = setterFactory;
    return this;
  }

  @Override
  public Contract enrich(Contract contract) {
    return new HystrixDelegatingContract(contract);
  }

  @Override
  public InvocationHandlerFactory enrich(InvocationHandlerFactory invocationHandlerFactory) {
    return (target, dispatch) -> new HystrixInvocationHandler(target, dispatch, setterFactory,
        fallbacks.containsKey(target.type())
            ? new FallbackFactory.Default<>(fallbacks.get(target.type()))
            : null);
  }

  public <E> Capability fallback(Class<E> api, E fallback) {
    fallbacks.put(api, fallback);

    return this;
  }

}
