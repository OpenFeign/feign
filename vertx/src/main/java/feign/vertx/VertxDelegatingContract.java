/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.vertx;

import static feign.Types.resolveLastTypeParameter;
import static feign.Util.checkNotNull;

import feign.Contract;
import feign.MethodMetadata;
import io.vertx.core.Future;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Contract allowing only {@link Future} return type.
 *
 * @author Alexei KLENIN
 */
public final class VertxDelegatingContract implements Contract {
  private final Contract delegate;

  public VertxDelegatingContract(final Contract delegate) {
    this.delegate = checkNotNull(delegate, "delegate must not be null");
  }

  @Override
  public List<MethodMetadata> parseAndValidateMetadata(final Class<?> targetType) {
    checkNotNull(targetType, "Argument targetType must be not null");

    final List<MethodMetadata> metadatas = delegate.parseAndValidateMetadata(targetType);

    for (final MethodMetadata metadata : metadatas) {
      final Type type = metadata.returnType();

      if (type instanceof ParameterizedType
          && ((ParameterizedType) type).getRawType().equals(Future.class)) {
        final Type actualType = resolveLastTypeParameter(type, Future.class);
        metadata.returnType(actualType);
      } else {
        throw new IllegalStateException(
            String.format(
                "Method %s of contract %s doesn't returns io.vertx.core.Future",
                metadata.configKey(), targetType.getSimpleName()));
      }
    }

    return metadatas;
  }
}
