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
package feign.form.spring;

import java.io.IOException;
import lombok.val;
import org.springframework.web.multipart.MultipartFile;
import feign.codec.EncodeException;
import feign.form.multipart.AbstractWriter;
import feign.form.multipart.Output;

/**
 * A Spring single file writer.
 *
 * @author Artem Labazin
 */
public class SpringSingleMultipartFileWriter extends AbstractWriter {

  @Override
  public boolean isApplicable(Object value) {
    return value instanceof MultipartFile;
  }

  @Override
  protected void write(Output output, String key, Object value) throws EncodeException {
    val file = (MultipartFile) value;
    writeFileMetadata(output, key, file.getOriginalFilename(), file.getContentType());

    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException ex) {
      throw new EncodeException("Getting multipart file's content bytes error", ex);
    }
    output.write(bytes);
  }
}
