/*
 * Copyright 2019 the original author or authors.
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

import java.net.URLConnection;

import feign.codec.EncodeException;
import lombok.SneakyThrows;
import lombok.val;

/**
 *
 * @author Artem Labazin
 */
public abstract class AbstractWriter implements Writer {

  @Override
  public void write (Output output, String boundary, String key, Object value) throws EncodeException {
    output.write("--").write(boundary).write(CRLF);
    write(output, key, value);
    output.write(CRLF);
  }

  /**
   * Writes data for it's children.
   *
   * @param output  output writer.
   * @param key     name for piece of data.
   * @param value   piece of data.
   *
   * @throws EncodeException in case of write errors
   */
  @SuppressWarnings({
      "PMD.UncommentedEmptyMethodBody",
      "PMD.EmptyMethodInAbstractClassShouldBeAbstract"
  })
  protected void write (Output output, String key, Object value) throws EncodeException {
  }

  /**
   * Writes file's metadata.
   *
   * @param output      output writer.
   * @param name        name for piece of data.
   * @param fileName    file name.
   * @param contentType type of file content. May be the {@code null}, in that case it will be determined by file name.
   */
  @SneakyThrows
  protected void writeFileMetadata (Output output, String name, String fileName, String contentType) {
    val contentDespositionBuilder = new StringBuilder()
        .append("Content-Disposition: form-data; name=\"").append(name).append("\"");
    if (fileName != null) {
      contentDespositionBuilder.append("; ").append("filename=\"").append(fileName).append("\"");
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

    val string = new StringBuilder()
        .append(contentDespositionBuilder.toString()).append(CRLF)
        .append("Content-Type: ").append(fileContentType).append(CRLF)
        .append("Content-Transfer-Encoding: binary").append(CRLF)
        .append(CRLF)
        .toString();

    output.write(string);
  }
}
