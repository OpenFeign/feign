/*
 * Copyright 2019 the original author or authors.
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

package feign.form.spring;

import static lombok.AccessLevel.PRIVATE;

import feign.codec.EncodeException;
import feign.form.multipart.AbstractWriter;
import feign.form.multipart.Output;

import lombok.experimental.FieldDefaults;
import lombok.val;
import org.springframework.web.multipart.MultipartFile;

/**
 *
 * @author Artem Labazin
 */
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class SpringManyMultipartFilesWriter extends AbstractWriter {

  SpringSingleMultipartFileWriter fileWriter = new SpringSingleMultipartFileWriter();

  @Override
  public void write (Output output, String boundary, String key, Object value) throws EncodeException {
    if (value instanceof MultipartFile[]) {
      val files = (MultipartFile[]) value;
      for (val file : files) {
        fileWriter.write(output, boundary, key, file);
      }
    } else if (value instanceof Iterable) {
      val iterable = (Iterable<?>) value;
      for (val file : iterable) {
        fileWriter.write(output, boundary, key, file);
      }
    }
  }

  @Override
  public boolean isApplicable (Object value) {
    if (value == null) {
      return false;
    }
    if (value instanceof MultipartFile[]) {
      return true;
    }
    if (value instanceof Iterable) {
      val iterable = (Iterable<?>) value;
      val iterator = iterable.iterator();
      if (iterator.hasNext() && iterator.next() instanceof MultipartFile) {
        return true;
      }
    }
    return false;
  }
}
