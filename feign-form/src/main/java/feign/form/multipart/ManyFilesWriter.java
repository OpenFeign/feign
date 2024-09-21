/*
 * Copyright 2012-2024 The Feign Authors
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
package feign.form.multipart;

import static lombok.AccessLevel.PRIVATE;
import java.io.File;
import lombok.experimental.FieldDefaults;
import lombok.val;
import feign.codec.EncodeException;

/**
 * A writer for multiple files.
 *
 * @author Artem Labazin
 */
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class ManyFilesWriter extends AbstractWriter {

  SingleFileWriter fileWriter = new SingleFileWriter();

  @Override
  public boolean isApplicable(Object value) {
    if (value instanceof File[]) {
      return true;
    }
    if (!(value instanceof Iterable)) {
      return false;
    }
    val iterable = (Iterable<?>) value;
    val iterator = iterable.iterator();
    return iterator.hasNext() && iterator.next() instanceof File;
  }

  @Override
  public void write(Output output, String boundary, String key, Object value)
      throws EncodeException {
    if (value instanceof File[]) {
      val files = (File[]) value;
      for (val file : files) {
        fileWriter.write(output, boundary, key, file);
      }
    } else if (value instanceof Iterable) {
      val iterable = (Iterable<?>) value;
      for (val file : iterable) {
        fileWriter.write(output, boundary, key, file);
      }
    } else {
      throw new IllegalArgumentException();
    }
  }
}
