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

package feign.form.multipart;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import feign.codec.EncodeException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 *
 * @author Artem Labazin
 */
@Slf4j
public class SingleFileWriter extends AbstractWriter {

  @Override
  public boolean isApplicable (Object value) {
    return value instanceof File;
  }

  @Override
  protected void write (Output output, String key, Object value) throws EncodeException {
    val file = (File) value;
    writeFileMetadata(output, key, file.getName(), null);

    InputStream input = null;
    try {
      input = new FileInputStream(file);
      val buf = new byte[1024];
      int length = input.read(buf);
      while (length > 0) {
        output.write(buf, 0, length);
        length = input.read(buf);
      }
    } catch (IOException ex) {
      val message = String.format("Writing file's '%s' content error", file.getName());
      throw new EncodeException(message, ex);
    } finally {
      if (input != null) {
        try {
          input.close();
        } catch (IOException ex) {
          log.error("Closing file '{}' error", file.getName(), ex);
        }
      }
    }
  }
}
