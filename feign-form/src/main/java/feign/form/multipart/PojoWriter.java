/*
 * Copyright 2018 Artem Labazin
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

import static lombok.AccessLevel.PRIVATE;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.val;

@FieldDefaults(level = PRIVATE, makeFinal = true)
public class PojoWriter extends AbstractWriter {

  ParameterWriter parameterWriter = new ParameterWriter();

  @Override
  public boolean isApplicable (Object object) {
    val type = object.getClass();
    val packageName = type.getPackage().getName();
    return !packageName.startsWith("java.");
  }

  @Override
  @SuppressWarnings("unchecked")
  public void write (Output output, String boundary, String key, Object value) throws Exception {
    val result = new HashMap<String, Object>();
    if (value instanceof Map) {
      val map = (Map<Object, Object>) value;
      for (val entry : map.entrySet()) {
        result.put(entry.getKey().toString(), entry.getValue());
      }
    } else {
      val type = value.getClass();
      for (val field : type.getDeclaredFields()) {
        AccessController.doPrivileged(new SetAccessibleAction(field));

        val fieldValue = field.get(value);
        if (fieldValue == null) {
          continue;
        }
        result.put(field.getName(), fieldValue.toString());
      }
    }

    for (val entry : result.entrySet()) {
      parameterWriter.write(output, boundary, entry.getKey(), entry.getValue());
    }
  }

  @RequiredArgsConstructor
  @FieldDefaults(level = PRIVATE, makeFinal = true)
  private class SetAccessibleAction implements PrivilegedAction<Object> {

    Field field;

    @Override
    public Object run () {
      field.setAccessible(true);
      return null;
    }
  }
}
