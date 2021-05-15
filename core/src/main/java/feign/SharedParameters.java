/**
 * Copyright 2012-2021 The Feign Authors
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

import feign.InvocationHandlerFactory.MethodHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

public class SharedParameters {

  private final Map<String, Object> paramNameToValue = new HashMap<>();

  public ConfigurationMethodHandler newHandler(final MethodMetadata methodMetadata) {
    return new ConfigurationMethodHandler(methodMetadata);
  }

  public Map<String, Object> asMap() {
    return paramNameToValue;
  }

  public class ConfigurationMethodHandler implements MethodHandler {

    private final MethodMetadata methodMetadata;

    public ConfigurationMethodHandler(final MethodMetadata methodMetadata) {
      this.methodMetadata = methodMetadata;
    }

    @Override
    public Object invoke(Object[] argv) throws Throwable {
      IntStream.range(0, argv.length)
          .forEach(index -> methodMetadata.indexToName()
              .get(index)
              .forEach(name -> paramNameToValue.put(name, argv[index])));
      return null;
    }
  }
}
