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
package feign.utils;

import feign.Util;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public final class ContentTypeParser {

  private ContentTypeParser() {}

  /**
   * Parses and returns information about the Content-Type header
   *
   * @param headers the headers to parse
   * @return a ContentTypeResult that has the content-type information (or Optional.empty() if the
   *     Content-Type header is not in the headers)
   */
  public static Optional<ContentTypeResult> parseContentTypeFromHeaders(
      Map<String, Collection<String>> headers) {
    // The header map *should* be a case insensitive treemap
    for (String val : headers.getOrDefault(Util.CONTENT_TYPE, Collections.emptyList())) {
      return Optional.of(parseContentTypeHeader(val));
    }

    return Optional.empty();
  }

  /**
   * Parses and returns information about a string that is a valid formatting Content-Type header
   * value
   *
   * @param contentTypeHeader the Content-Type header value to parse
   * @return a ContentTypeResult that has the content-type information (or Optional.empty() if the
   *     Content-Type header is not in the headers)
   */
  public static ContentTypeResult parseContentTypeHeader(String contentTypeHeader) {

    String[] contentTypeParmeters = contentTypeHeader.split(";");
    String contentType = contentTypeParmeters[0];
    String charsetString = "";
    if (contentTypeParmeters.length > 1) {
      String[] charsetParts = contentTypeParmeters[1].split("=");
      if (charsetParts.length == 2 && "charset".equalsIgnoreCase(charsetParts[0].trim())) {
        // TODO: 20260727 - this doesn't implement the full parser definition for the content-type
        // header (esp related to quoted strings, etc...) - see
        // https://www.w3.org/Protocols/rfc1341/4_Content-Type.html
        charsetString = charsetParts[1].trim();
        if (charsetString.length() > 1
            && charsetString.startsWith("\"")
            && charsetString.endsWith("\""))
          charsetString = charsetString.substring(1, charsetString.length() - 1);
      }
    }

    return new ContentTypeResult(contentType, charsetOrNull(charsetString));
  }

  private static Charset charsetOrNull(String charsetStr) {

    try {
      return Charset.forName(charsetStr);
    } catch (Exception e) {
      return null;
    }
  }

  /** Represents the parsed results of a Content-Type header */
  public static class ContentTypeResult {
    public static final ContentTypeResult MISSING = new ContentTypeResult("", null);

    /** The content type portion of the header string */
    private String contentType;

    /** The charset portion of the header string (if specified) */
    private Optional<Charset> charset;

    public ContentTypeResult(String contentType, Charset charset) {
      this.contentType = contentType;
      this.charset = Optional.ofNullable(charset);
    }

    public String getContentType() {
      return contentType;
    }

    public Optional<Charset> getCharset() {
      return charset;
    }
  }
}
