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
import java.net.URLConnection;

/**
 * A base writer class.
 *
 * @author Artem Labazin
 */
public abstract class AbstractWriter implements Writer {

  @Override
  public void write(Output output, String boundary, String key, Object value)
      throws EncodeException {
    output.write("--").write(boundary).write(CRLF);
    write(output, key, value);
    output.write(CRLF);
  }

  /**
   * Writes data for it's children.
   *
   * @param output output writer.
   * @param key name for piece of data.
   * @param value piece of data.
   * @throws EncodeException in case of write errors
   */
  @SuppressWarnings({
    "PMD.UncommentedEmptyMethodBody",
    "PMD.EmptyMethodInAbstractClassShouldBeAbstract"
  })
  protected void write(Output output, String key, Object value) throws EncodeException {}

  /**
   * Writes file's metadata.
   *
   * @param output output writer.
   * @param name name for piece of data.
   * @param fileName file name.
   * @param contentType type of file content. May be the {@code null}, in that case it will be
   *     determined by file name.
   */
  protected void writeFileMetadata(
      Output output, String name, String fileName, String contentType) {
    final var contentDespositionBuilder =
        new StringBuilder()
            .append("Content-Disposition: form-data; name=\"")
            .append(escapeHeaderParameter(name))
            .append("\"");
    if (fileName != null) {
      contentDespositionBuilder
          .append("; ")
          .append("filename=\"")
          .append(escapeHeaderParameter(fileName))
          .append("\"");
    }

    String fileContentType = contentType;
    if (fileContentType == null) {
      if (fileName != null) {
        fileContentType = URLConnection.guessContentTypeFromName(fileName);
      }
      if (fileContentType == null) {
        fileContentType = "application/octet-stream";
      }
    }

    final var string =
        new StringBuilder()
            .append(contentDespositionBuilder.toString())
            .append(CRLF)
            .append("Content-Type: ")
            .append(stripCrlf(fileContentType))
            .append(CRLF)
            .append("Content-Transfer-Encoding: binary")
            .append(CRLF)
            .append(CRLF)
            .toString();

    output.write(string);
  }

  /**
   * Escapes a {@code multipart/form-data} header parameter value so an attacker-supplied name or
   * file name cannot break out of the quoted string and inject extra headers or part boundaries.
   * Carriage return, line feed and double quote are percent-encoded, matching the WHATWG form-data
   * encoding rules.
   *
   * @param value the raw parameter value.
   * @return the escaped value, safe to place inside a quoted header parameter.
   */
  protected static String escapeHeaderParameter(String value) {
    return value.replace("\r", "%0D").replace("\n", "%0A").replace("\"", "%22");
  }

  /**
   * Removes carriage return and line feed from a media type so an attacker-supplied content type
   * cannot inject extra part headers or boundaries. Unlike a quoted parameter the {@code
   * Content-Type} value is not quoted, so it cannot be percent-encoded without corrupting a
   * legitimate media type; the control characters are dropped instead.
   *
   * @param contentType the raw content type value.
   * @return the content type with CR and LF removed.
   */
  protected static String stripCrlf(String contentType) {
    return contentType.replace("\r", "").replace("\n", "");
  }
}
