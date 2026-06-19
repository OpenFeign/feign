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

/** A factory for creating part headers based on a {@link PartContext}. */
public class PartHeadersFactory {
  private static final char DOUBLE_QUOTE = '"';

  /**
   * Escapes a {@code multipart/form-data} header parameter value so an attacker-supplied name or
   * file name cannot break out of the quoted string and inject extra headers or part boundaries.
   * Carriage return, line feed and double quote are percent-encoded, matching the WHATWG form-data
   * encoding rules.
   *
   * @param value the raw parameter value.
   * @return the escaped value, safe to place inside a quoted header parameter.
   */
  private static String escapeHeaderParameter(String value) {
    return value.replace("\r", "%0D").replace("\n", "%0A").replace("\"", "%22");
  }

  /**
   * Creates part headers based on the given {@link PartContext}.
   *
   * @param partContext the context to create part headers from
   * @return a map of part headers
   */
  public Map<String, String> create(PartContext partContext) {
    var headers = new LinkedHashMap<String, String>();
    var disposition =
        new StringBuilder("form-data; name=")
            .append(DOUBLE_QUOTE)
            .append(escapeHeaderParameter(partContext.name()))
            .append(DOUBLE_QUOTE);

    partContext
        .filename()
        .ifPresent(
            filename ->
                disposition
                    .append("; filename=")
                    .append(DOUBLE_QUOTE)
                    .append(escapeHeaderParameter(filename))
                    .append(DOUBLE_QUOTE));

    headers.put("Content-Disposition", disposition.toString());

    partContext.contentType().ifPresent(contentType -> headers.put(Util.CONTENT_TYPE, contentType));

    return headers;
  }
}
