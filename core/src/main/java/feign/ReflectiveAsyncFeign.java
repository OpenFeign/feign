/*
 * Copyright 2012-2022 The Feign Authors
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
import java.lang.reflect.WildcardType;
import java.util.concurrent.CompletableFuture;

@Experimental
public class ReflectiveAsyncFeign<C> extends AsyncFeign<C> {

  public ReflectiveAsyncFeign(ReflectiveFeign<C> feign,
      AsyncContextSupplier<C> defaultContextSupplier) {
    super(feign, defaultContextSupplier);
  }

  private String getFullMethodName(Class<?> type, Type retType, Method m) {
    return retType.getTypeName() + " " + type.toGenericString() + "." + m.getName();
  }

  @Override
  protected <T> T wrap(Class<T> type, T instance, C context) {
    if (!type.isInterface()) {
      throw new IllegalArgumentException("Type must be an interface: " + type);
    }

    for (final Method m : type.getMethods()) {
      final Class<?> retType = m.getReturnType();

      if (!CompletableFuture.class.isAssignableFrom(retType)) {
        continue; // synchronous case
      }

      if (retType != CompletableFuture.class) {
        throw new IllegalArgumentException("Method return type is not CompleteableFuture: "
            + getFullMethodName(type, retType, m));
      }

      final Type genRetType = m.getGenericReturnType();

      if (!ParameterizedType.class.isInstance(genRetType)) {
        throw new IllegalArgumentException("Method return type is not parameterized: "
            + getFullMethodName(type, genRetType, m));
      }

      if (WildcardType.class
          .isInstance(ParameterizedType.class.cast(genRetType).getActualTypeArguments()[0])) {
        throw new IllegalArgumentException(
            "Wildcards are not supported for return-type parameters: "
                + getFullMethodName(type, genRetType, m));
      }
    }

    return instance;
  }
}
