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
package feign.optionals;

import feign.Response;
import feign.Util;
import feign.codec.Decoder;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Optional;

public final class OptionalDecoder implements Decoder {
  final Decoder delegate;

  public OptionalDecoder(Decoder delegate) {
    Objects.requireNonNull(delegate, "Decoder must not be null. ");
    this.delegate = delegate;
  }

  @Override
  public Object decode(Response response, Type type) throws IOException {
    if (!isOptional(type)) {
      return delegate.decode(response, type);
    }
    if (response.status() == 404 || response.status() == 204) {
      return Optional.empty();
    }
    Type enclosedType = Util.resolveLastTypeParameter(type, Optional.class);
    return Optional.ofNullable(delegate.decode(response, enclosedType));
  }

  static boolean isOptional(Type type) {
    if (!(type instanceof ParameterizedType)) {
      return false;
    }
    ParameterizedType parameterizedType = (ParameterizedType) type;
    return parameterizedType.getRawType().equals(Optional.class);
  }
}
