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
package feign;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import feign.Logger.Level;
import feign.Request.Options;
import feign.codec.Decoder;
import feign.codec.Encoder;

/**
 * Capabilities expose core feign artifacts to implementations so parts of core can be customized
 * around the time the client being built.
 *
 * For instance, capabilities take the {@link Client}, make changes to it and feed the modified
 * version back to feign.
 *
 * @see Metrics5Capability
 */
public interface Capability {


  static <E> E enrich(E componentToEnrich, List<Capability> capabilities) {
    return capabilities.stream()
        // invoke each individual capability and feed the result to the next one.
        // This is equivalent to:
        // Capability cap1 = ...;
        // Capability cap2 = ...;
        // Capability cap2 = ...;
        // Contract contract = ...;
        // Contract contract1 = cap1.enrich(contract);
        // Contract contract2 = cap2.enrich(contract1);
        // Contract contract3 = cap3.enrich(contract2);
        // or in a more compact version
        // Contract enrichedContract = cap3.enrich(cap2.enrich(cap1.enrich(contract)));
        .reduce(
            componentToEnrich,
            (component, capability) -> invoke(component, capability),
            (component, enrichedComponent) -> enrichedComponent);
  }

  static <E> E invoke(E target, Capability capability) {
    return Arrays.stream(capability.getClass().getMethods())
        .filter(method -> method.getName().equals("enrich"))
        .filter(method -> method.getReturnType().isInstance(target))
        .findFirst()
        .map(method -> {
          try {
            return (E) method.invoke(capability, target);
          } catch (IllegalAccessException | IllegalArgumentException
              | InvocationTargetException e) {
            throw new RuntimeException("Unable to enrich " + target, e);
          }
        })
        .orElse(target);
  }

  default Client enrich(Client client) {
    return client;
  }

  default Retryer enrich(Retryer retryer) {
    return retryer;
  }

  default RequestInterceptor enrich(RequestInterceptor requestInterceptor) {
    return requestInterceptor;
  }

  default Logger enrich(Logger logger) {
    return logger;
  }

  default Level enrich(Level level) {
    return level;
  }

  default Contract enrich(Contract contract) {
    return contract;
  }

  default Options enrich(Options options) {
    return options;
  }

  default Encoder enrich(Encoder encoder) {
    return encoder;
  }

  default Decoder enrich(Decoder decoder) {
    return decoder;
  }

  default InvocationHandlerFactory enrich(InvocationHandlerFactory invocationHandlerFactory) {
    return invocationHandlerFactory;
  }

  default QueryMapEncoder enrich(QueryMapEncoder queryMapEncoder) {
    return queryMapEncoder;
  }

}
