/*
 * Copyright 2017 Artem Labazin <xxlabaza@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package feign.form.multipart;

/**
 *
 * @author Artem Labazin <xxlabaza@gmail.com>
 */
public class ByteArrayWriter extends AbstractWriter {

  @Override
  public boolean isApplicable (Object value) {
    return value != null && value instanceof byte[];
  }

  @Override
  protected void write (Output output, String key, Object value) throws Exception {
    writeFileMetadata(output, key, null, null);

    byte[] bytes = (byte[]) value;
    output.write(bytes);
  }
}
