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

import feign.Util;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base class for {@link PartEncoder} implementations that provides common functionality for
 * creating headers for multipart form data parts.
 */
public abstract class AbstractPartEncoder implements PartEncoder {
  private static final char DOUBLE_QUOTE = '"';

  /**
   * Creates headers for a multipart form data part based on the provided name, filename, and
   * content type.
   *
   * @param name the name of the form field
   * @param filename the optional filename to include in the {@code Content-Disposition} header; may
   *     be {@code null}
   * @param contentType the optional content type to include in the {@code Content-Type} header; may
   *     be {@code null}
   * @return a map of headers for the multipart form data part
   */
  protected Map<String, String> createHeaders(String name, String filename, String contentType) {
    var headers = new LinkedHashMap<String, String>();
    var disposition =
        new StringBuilder("form-data; name=")
            .append(DOUBLE_QUOTE)
            .append(name)
            .append(DOUBLE_QUOTE);

    if (filename != null) {
      disposition.append("; filename=").append(DOUBLE_QUOTE).append(filename).append(DOUBLE_QUOTE);
    }

    headers.put("Content-Disposition", disposition.toString());

    if (contentType != null) {
      headers.put(Util.CONTENT_TYPE, contentType);
    }

    return headers;
  }
}
