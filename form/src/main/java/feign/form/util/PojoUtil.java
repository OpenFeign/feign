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
package feign.form.util;

import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isStatic;
import static lombok.AccessLevel.PRIVATE;

import feign.form.FormProperty;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.rmi.UnexpectedException;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.FieldDefaults;

/**
 * An utility class to work with POJOs.
 *
 * @author Artem Labazin
 */
public final class PojoUtil {

  public static boolean isUserPojo(@NonNull Object object) {
    final var type = object.getClass();
    final var packageName = type.getPackage().getName();
    return !packageName.startsWith("java.");
  }

  public static boolean isUserPojo(@NonNull Type type) {
    final var typeName = type.toString();
    return !typeName.startsWith("class java.");
  }

  @SneakyThrows
  public static Map<String, Object> toMap(@NonNull Object object) {
    final var result = new HashMap<String, Object>();
    final var type = object.getClass();
    for (var field : type.getDeclaredFields()) {
      final var modifiers = field.getModifiers();
      if (isFinal(modifiers) || isStatic(modifiers)) {
        continue;
      }
      field.setAccessible(true);

      final var fieldValue = field.get(object);
      if (fieldValue == null) {
        continue;
      }

      final var propertyKey =
          field.isAnnotationPresent(FormProperty.class)
              ? field.getAnnotation(FormProperty.class).value()
              : field.getName();

      result.put(propertyKey, fieldValue);
    }
    return result;
  }

  private PojoUtil() throws UnexpectedException {
    throw new UnexpectedException("It is not allowed to instantiate this class");
  }

  @Setter
  @NoArgsConstructor
  @FieldDefaults(level = PRIVATE)
  private static final class SetAccessibleAction implements PrivilegedAction<Object> {

    Field field;

    @Override
    public Object run() {
      field.setAccessible(true);
      return null;
    }
  }
}
