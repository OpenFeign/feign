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

import static lombok.AccessLevel.PRIVATE;

import feign.codec.EncodeException;
import java.io.File;
import lombok.experimental.FieldDefaults;

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
    final var iterable = (Iterable<?>) value;
    final var iterator = iterable.iterator();
    return iterator.hasNext() && iterator.next() instanceof File;
  }

  @Override
  public void write(Output output, String boundary, String key, Object value)
      throws EncodeException {
    if (value instanceof File[]) {
      final var files = (File[]) value;
      for (var file : files) {
        fileWriter.write(output, boundary, key, file);
      }
    } else if (value instanceof Iterable) {
      final var iterable = (Iterable<?>) value;
      for (var file : iterable) {
        fileWriter.write(output, boundary, key, file);
      }
    } else {
      throw new IllegalArgumentException();
    }
  }
}
