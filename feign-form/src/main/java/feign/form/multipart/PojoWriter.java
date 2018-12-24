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
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.val;

@RequiredArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class PojoWriter extends AbstractWriter {

  List<Writer> writers;

  @Override
  public boolean isApplicable(Object object) {
    val type = object.getClass();
    val packageName = type.getPackage().getName();
    return !packageName.startsWith("java.");
  }

  @Override
  public void write(Output output, String boundary, String key, Object object) throws Exception {
    val type = object.getClass();
    for (val field : type.getDeclaredFields()) {
      AccessController.doPrivileged(new SetAccessibleAction(field));

      val value = field.get(object);
      if (value == null) {
        continue;
      }

      val writer = findApplicableWriter(value);
      if (writer == null) {
        continue;
      }

      val name = field.getName();
      writer.write(output, boundary, name, value);
    }
  }

  private Writer findApplicableWriter(Object value) {
    for (val writer : writers) {
      if (writer.isApplicable(value)) {
        return writer;
      }
    }
    return null;
  }

  @RequiredArgsConstructor
  @FieldDefaults(level = PRIVATE, makeFinal = true)
  private class SetAccessibleAction implements PrivilegedAction<Object> {

    Field field;

    @Override
    public Object run() {
      field.setAccessible(true);
      return null;
    }
  }
}
