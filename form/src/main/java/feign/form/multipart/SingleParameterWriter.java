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

import static feign.form.ContentProcessor.CRLF;

import feign.codec.EncodeException;
import lombok.val;

/**
 * A writer for a single parameter.
 *
 * @author Artem Labazin
 */
public class SingleParameterWriter extends AbstractWriter {

  @Override
  public boolean isApplicable(Object value) {
    return value instanceof Number || value instanceof CharSequence || value instanceof Boolean;
  }

  @Override
  protected void write(Output output, String key, Object value) throws EncodeException {
    writeWithContentType(output, key, value, null);
  }

  /**
   * Writes a single parameter using the given content type.
   *
   * @param output output writer.
   * @param key name for piece of data.
   * @param value piece of data.
   * @param contentType the content type of the part. May be {@code null}, in which case {@code
   *     text/plain} with the output charset is used.
   * @throws EncodeException in case of write errors
   */
  protected void writeWithContentType(Output output, String key, Object value, String contentType)
      throws EncodeException {
    val contentTypeHeader =
        contentType != null ? contentType : "text/plain; charset=" + output.getCharset().name();
    val string =
        new StringBuilder()
            .append("Content-Disposition: form-data; name=\"")
            .append(escapeHeaderParameter(key))
            .append('"')
            .append(CRLF)
            .append("Content-Type: ")
            .append(contentTypeHeader)
            .append(CRLF)
            .append(CRLF)
            .append(value.toString())
            .toString();

    output.write(string);
  }
}
