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

import static feign.form.ContentProcessor.CRLF;
import lombok.val;
import feign.codec.EncodeException;

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
    val string = new StringBuilder().append("Content-Disposition: form-data; name=\"").append(key)
        .append('"')
        .append(CRLF).append("Content-Type: text/plain; charset=")
        .append(output.getCharset().name())
        .append(CRLF).append(CRLF).append(value.toString()).toString();

    output.write(string);
  }
}
