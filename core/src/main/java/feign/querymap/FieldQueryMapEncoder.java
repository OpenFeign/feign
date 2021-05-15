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
package feign.querymap;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import feign.Param;
import feign.QueryMapEncoder;
import feign.codec.EncodeException;

/**
 * the query map will be generated using member variable names as query parameter names.
 *
 * eg: "/uri?name={name}&number={number}"
 *
 * order of included query parameters not guaranteed, and as usual, if any value is null, it will be
 * left out
 */
public class FieldQueryMapEncoder implements QueryMapEncoder {

  private final Map<Class<?>, ObjectParamMetadata> classToMetadata =
      new ConcurrentHashMap<>();

  @Override
  public Map<String, Object> encode(Object object) throws EncodeException {
    ObjectParamMetadata metadata =
        classToMetadata.computeIfAbsent(object.getClass(), ObjectParamMetadata::parseObjectType);

    return metadata.objectFields.stream()
        .map(field -> this.FieldValuePair(object, field))
        .filter(fieldObjectPair -> fieldObjectPair.right.isPresent())
        .collect(Collectors.toMap(this::fieldName,
            fieldObjectPair -> fieldObjectPair.right.get()));

  }

  private String fieldName(Pair<Field, Optional<Object>> pair) {
    Param alias = pair.left.getAnnotation(Param.class);
    return alias != null ? alias.value() : pair.left.getName();
  }

  private Pair<Field, Optional<Object>> FieldValuePair(Object object, Field field) {
    try {
      return Pair.pair(field, Optional.ofNullable(field.get(object)));
    } catch (IllegalAccessException e) {
      throw new EncodeException("Failure encoding object into query map", e);
    }
  }

  private static class ObjectParamMetadata {

    private final List<Field> objectFields;

    private ObjectParamMetadata(List<Field> objectFields) {
      this.objectFields = Collections.unmodifiableList(objectFields);
    }

    private static ObjectParamMetadata parseObjectType(Class<?> type) {
      List<Field> allFields = new ArrayList();

      for (Class currentClass = type; currentClass != null; currentClass =
          currentClass.getSuperclass()) {
        Collections.addAll(allFields, currentClass.getDeclaredFields());
      }

      return new ObjectParamMetadata(allFields.stream()
          .filter(field -> !field.isSynthetic())
          .peek(field -> field.setAccessible(true))
          .collect(Collectors.toList()));
    }
  }

  private static class Pair<T, U> {
    private Pair(T left, U right) {
      this.right = right;
      this.left = left;
    }

    public final T left;
    public final U right;

    public static <T, U> Pair<T, U> pair(T left, U right) {
      return new Pair<>(left, right);
    }

  }

}
