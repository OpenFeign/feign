/*
 * Copyright 2017 Artem Labazin <xxlabaza@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package feign.form.spring;

import feign.form.multipart.AbstractWriter;
import feign.form.multipart.Output;
import lombok.val;
import org.springframework.web.multipart.MultipartFile;

/**
 *
 * @author Artem Labazin <xxlabaza@gmail.com>
 */
public class SpringSingleMultipartFileWriter extends AbstractWriter {

  @Override
  public boolean isApplicable (Object value) {
    return value != null && value instanceof MultipartFile;
  }

  @Override
  protected void write (Output output, String key, Object value) throws Exception {
    val file = (MultipartFile) value;

    writeFileMetadata(output, key, file.getOriginalFilename(), file.getContentType());

    output.write(file.getBytes());
  }
}
