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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import lombok.val;
import feign.codec.EncodeException;

/**
 * A single-file writer.
 *
 * @author Artem Labazin
 */
public class SingleFileWriter extends AbstractWriter {

  @Override
  public boolean isApplicable(Object value) {
    return value instanceof File;
  }

  @Override
  protected void write(Output output, String key, Object value) throws EncodeException {
    val file = (File) value;
    writeFileMetadata(output, key, file.getName(), null);

    try (InputStream input = new FileInputStream(file)) {
      val buf = new byte[4096];
      int length = input.read(buf);
      while (length > 0) {
        output.write(buf, 0, length);
        length = input.read(buf);
      }
    } catch (IOException ex) {
      val message = String.format("Writing file's '%s' content error", file.getName());
      throw new EncodeException(message, ex);
    }
  }
}
