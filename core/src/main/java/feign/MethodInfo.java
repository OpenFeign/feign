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

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;

@Experimental
class MethodInfo {
  private final String configKey;
  private final Type underlyingReturnType;
  private final boolean asyncReturnType;

  MethodInfo(String configKey, Type underlyingReturnType, boolean asyncReturnType) {
    this.configKey = configKey;
    this.underlyingReturnType = underlyingReturnType;
    this.asyncReturnType = asyncReturnType;
  }

  MethodInfo(Class<?> targetType, Method method) {
    this.configKey = Feign.configKey(targetType, method);

    final Type type = method.getGenericReturnType();

    if (method.getReturnType() != CompletableFuture.class) {
      this.asyncReturnType = false;
      this.underlyingReturnType = type;
    } else {
      this.asyncReturnType = true;
      this.underlyingReturnType = ((ParameterizedType) type).getActualTypeArguments()[0];
    }
  }

  String configKey() {
    return configKey;
  }

  Type underlyingReturnType() {
    return underlyingReturnType;
  }

  boolean isAsyncReturnType() {
    return asyncReturnType;
  }
}
