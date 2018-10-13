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

import feign.Contract;
import feign.MethodMetadata;
import feign.Types;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.reactivestreams.Publisher;

public class ReactiveDelegatingContract implements Contract {

  private final Contract delegate;

  ReactiveDelegatingContract(Contract delegate) {
    this.delegate = delegate;
  }

  @Override
  public List<MethodMetadata> parseAndValidatateMetadata(Class<?> targetType) {
    List<MethodMetadata> methodsMetadata = this.delegate.parseAndValidatateMetadata(targetType);

    for (final MethodMetadata metadata : methodsMetadata) {
      final Type type = metadata.returnType();
      if (!isReactive(type)) {
        throw new IllegalArgumentException(String.format(
            "Method %s of contract %s doesn't returns a org.reactivestreams.Publisher",
            metadata.configKey(), targetType.getSimpleName()));
      }

      /*
       * we will need to change the return type of the method to match the return type contained
       * within the Publisher
       */
      Type[] actualTypes = ((ParameterizedType) type).getActualTypeArguments();
      if (actualTypes.length > 1) {
        throw new IllegalStateException("Expected only one contained type.");
      } else {
        Class<?> actual = Types.getRawType(actualTypes[0]);
        if (Stream.class.isAssignableFrom(actual)) {
          throw new IllegalArgumentException(
              "Streams are not supported when using Reactive Wrappers");
        }
        metadata.returnType(actualTypes[0]);
      }
    }

    return methodsMetadata;
  }

  /**
   * Ensure that the type provided implements a Reactive Streams Publisher.
   *
   * @param type to inspect.
   * @return true if the type implements the Reactive Streams Publisher specification.
   */
  private boolean isReactive(Type type) {
    if (!ParameterizedType.class.isAssignableFrom(type.getClass())) {
      return false;
    }
    ParameterizedType parameterizedType = (ParameterizedType) type;
    Type raw = parameterizedType.getRawType();
    return Arrays.asList(((Class) raw).getInterfaces())
        .contains(Publisher.class);
  }
}
