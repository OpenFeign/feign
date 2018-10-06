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
package feign.querymap;

import feign.QueryMapEncoder;
import feign.codec.EncodeException;
import java.lang.reflect.Field;
import java.util.*;

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
      new HashMap<Class<?>, ObjectParamMetadata>();

  @Override
  public Map<String, Object> encode(Object object) throws EncodeException {
    try {
      ObjectParamMetadata metadata = getMetadata(object.getClass());
      Map<String, Object> fieldNameToValue = new HashMap<String, Object>();
      for (Field field : metadata.objectFields) {
        Object value = field.get(object);
        if (value != null && value != object) {
          fieldNameToValue.put(field.getName(), value);
        }
      }
      return fieldNameToValue;
    } catch (IllegalAccessException e) {
      throw new EncodeException("Failure encoding object into query map", e);
    }
  }

  private ObjectParamMetadata getMetadata(Class<?> objectType) {
    ObjectParamMetadata metadata = classToMetadata.get(objectType);
    if (metadata == null) {
      metadata = ObjectParamMetadata.parseObjectType(objectType);
      classToMetadata.put(objectType, metadata);
    }
    return metadata;
  }

  private static class ObjectParamMetadata {

    private final List<Field> objectFields;

    private ObjectParamMetadata(List<Field> objectFields) {
      this.objectFields = Collections.unmodifiableList(objectFields);
    }

    private static ObjectParamMetadata parseObjectType(Class<?> type) {
      List<Field> fields = new ArrayList<Field>();
      for (Field field : type.getDeclaredFields()) {
        if (!field.isAccessible()) {
          field.setAccessible(true);
        }
        fields.add(field);
      }
      return new ObjectParamMetadata(fields);
    }
  }
}
