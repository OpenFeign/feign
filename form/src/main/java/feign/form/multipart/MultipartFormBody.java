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

import feign.Request;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** A multipart form body that can be used to send {@code multipart/form-data} requests. */
@Data
@RequiredArgsConstructor
@Accessors(fluent = true)
public class MultipartFormBody implements Request.Body {
  private static final String CRLF = "\r\n";
  private static final String DOUBLE_DASH = "--";

  @NonNull private final Collection<Request.Body> parts;

  @NonNull private final String boundary;

  /**
   * Creates a new {@link MultipartFormBody} with the given parts and a random boundary.
   *
   * @param parts the parts of the multipart form body
   */
  public MultipartFormBody(List<Request.Body> parts) {
    this(parts, UUID.randomUUID().toString());
  }

  /**
   * Writes the multipart form body to the given output stream. The body is written in the following
   * format:
   *
   * <pre>{@code
   * --boundary
   * headers
   *
   * body
   * --boundary
   * headers
   *
   * body
   * ...
   * --boundary--
   * }</pre>
   *
   * @param outputStream {@inheritDoc}
   * @throws IOException {@inheritDoc}
   */
  @Override
  public void writeTo(OutputStream outputStream) throws IOException {
    for (var part : parts) {
      writeString(outputStream, DOUBLE_DASH, boundary, CRLF);
      part.writeTo(outputStream);
      writeString(outputStream, CRLF);
    }

    writeString(outputStream, DOUBLE_DASH, boundary, DOUBLE_DASH, CRLF);
  }

  /**
   * {@inheritDoc}
   *
   * @return the content length of the multipart form body, or {@code -1} if any of the parts has an
   *     unknown content length
   */
  @Override
  public long contentLength() {
    var partsLengths = parts.stream().mapToLong(Request.Body::contentLength).summaryStatistics();

    return partsLengths.getMin() < 0
        ? Request.Body.super.contentLength()
        : partsLengths.getSum() + (6L + boundary.length()) * (parts.size() + 1);
  }

  /**
   * {@inheritDoc}
   *
   * @return {@code true} if all parts of the multipart form body are repeatable, {@code false}
   *     otherwise
   */
  @Override
  public boolean isRepeatable() {
    return parts.stream().allMatch(Request.Body::isRepeatable);
  }

  /**
   * {@inheritDoc}
   *
   * @return a string representation of the multipart form body, which includes the boundary and the
   *     string representation of each part
   */
  @Override
  public String toString() {
    var builder = new StringBuilder();

    for (var part : parts) {
      builder.append(DOUBLE_DASH).append(boundary).append(CRLF).append(part).append(CRLF);
    }

    return builder.append(DOUBLE_DASH).append(boundary).append(DOUBLE_DASH).append(CRLF).toString();
  }

  private void writeString(OutputStream outputStream, String... values) throws IOException {
    for (var value : values) {
      outputStream.write(value.getBytes(StandardCharsets.UTF_8));
    }
  }
}
