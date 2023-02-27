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
package feign;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;

@Experimental
public class MethodInfo {
  private final Type underlyingReturnType;
  private final boolean asyncReturnType;

  protected MethodInfo(Type underlyingReturnType, boolean asyncReturnType) {
    this.underlyingReturnType = underlyingReturnType;
    this.asyncReturnType = asyncReturnType;
  }

  MethodInfo(Class<?> targetType, Method method) {
    final Type type = Types.resolve(targetType, targetType, method.getGenericReturnType());

    if (type instanceof ParameterizedType
        && Types.getRawType(type).isAssignableFrom(CompletableFuture.class)) {
      this.asyncReturnType = true;
      this.underlyingReturnType = ((ParameterizedType) type).getActualTypeArguments()[0];
    } else {
      this.asyncReturnType = false;
      this.underlyingReturnType = type;
    }
  }

  Type underlyingReturnType() {
    return underlyingReturnType;
  }

  boolean isAsyncReturnType() {
    return asyncReturnType;
  }
}
