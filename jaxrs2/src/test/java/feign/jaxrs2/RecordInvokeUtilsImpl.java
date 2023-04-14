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
package feign.jaxrs2;

import static java.lang.invoke.MethodType.methodType;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import feign.utils.RecComponent;
import feign.utils.RecordInvokeUtils;

final class RecordInvokeUtilsImpl extends RecordInvokeUtils {
  @SuppressWarnings("unchecked")
  public static <T> T invokeCanonicalConstructor(Class<T> recordType, Object[] args) {
    try {
      RecComponent[] recordComponents = recordComponents(recordType, null);
      Type[] paramTypes =
          Arrays.stream(recordComponents).map(RecComponent::type).toArray(Type[]::new);
      paramTypes = Arrays.stream(paramTypes)
          .map(v -> v instanceof ParameterizedType ? (Class<?>) ((ParameterizedType) v).getRawType()
              : (Class<?>) v)
          .toArray(Class[]::new);
      MethodHandle MH_canonicalConstructor = LOOKUP
          .findConstructor(recordType, methodType(void.class, (Class<?>[]) paramTypes))
          .asType(methodType(Object.class, (Class<?>[]) paramTypes));
      return (T) MH_canonicalConstructor.invokeWithArguments(args);
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException("Could not construct type (" + recordType.getName() + ")");
    }
  }
}
