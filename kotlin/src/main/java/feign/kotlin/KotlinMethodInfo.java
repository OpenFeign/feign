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
package feign.kotlin;

import feign.Feign;
import feign.MethodInfo;
import feign.Types;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;

class KotlinMethodInfo extends MethodInfo {

  KotlinMethodInfo(Type underlyingReturnType, boolean asyncReturnType) {
    super(underlyingReturnType, asyncReturnType);
  }

  static KotlinMethodInfo createInstance(Class<?> targetType, Method method) {
    final Type type = Types.resolve(targetType, targetType, method.getGenericReturnType());

    Type underlyingReturnType;
    boolean asyncReturnType;
    if (MethodKt.isSuspend(method)) {
      asyncReturnType = true;
      underlyingReturnType = MethodKt.getKotlinMethodReturnType(method);
      if (underlyingReturnType == null) {
        String configKey = Feign.configKey(targetType, method);
        throw new IllegalArgumentException(
            String.format(
                "Method %s can't have continuation argument, only kotlin method is allowed",
                configKey));
      }
    } else if (type instanceof ParameterizedType
        && Types.getRawType(type).isAssignableFrom(CompletableFuture.class)) {
      asyncReturnType = true;
      underlyingReturnType = ((ParameterizedType) type).getActualTypeArguments()[0];
    } else {
      asyncReturnType = false;
      underlyingReturnType = type;
    }

    return new KotlinMethodInfo(underlyingReturnType, asyncReturnType);
  }
}
