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

import feign.form.FormData;

import lombok.val;

public class FormDataWriter extends AbstractWriter {
  @Override
  public boolean isApplicable (Object value) {
    return value instanceof FormData;
  }

  @Override
  protected void write (Output output, String key, Object value) throws Exception {
    val formData = (FormData) value;
    writeFileMetadata(output, key, null, formData.getContentType());
    output.write(formData.getData());
  }
}
