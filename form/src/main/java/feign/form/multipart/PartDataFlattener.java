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
package feign.form.multipart;

import feign.PartData;
import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * A utility class that flattens a {@link PartData} object into a stream of {@link PartData}
 * objects.
 */
@UtilityClass
class PartDataFlattener {
  /**
   * Flattens a {@link PartData} object into a stream of {@link PartData} objects.
   *
   * @param partData the {@link PartData} object to flatten
   * @return a stream of {@link PartData} objects
   */
  Stream<PartData> flatten(PartData partData) {
    var value = partData.value();

    if (!partData.explode() || value == null) {
      return Stream.of(partData);
    }

    if (value instanceof Collection) {
      return flatten(partData, getTypeArgument(partData.type()), ((Collection<?>) value).stream());
    }

    if (value.getClass().isArray() && !(value instanceof byte[])) {
      return flatten(partData, value.getClass().getComponentType(), arrayToStream(value));
    }

    return Stream.of(partData);
  }

  private Stream<PartData> flatten(PartData original, Type type, Stream<?> values) {
    return values.map(value -> new PartData(type, value, original.headers(), original.explode()));
  }

  private Type getTypeArgument(Type type) {
    return type instanceof ParameterizedType
        ? ((ParameterizedType) type).getActualTypeArguments()[0]
        : Object.class;
  }

  private Stream<Object> arrayToStream(Object array) {
    return IntStream.range(0, Array.getLength(array)).mapToObj(i -> Array.get(array, i));
  }
}
