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
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/** A multipart part consisting of headers and a body. */
@Data
@RequiredArgsConstructor
public class Part implements Request.Body {
  private static final String CRLF = "\r\n";

  @NonNull private final Map<String, String> headers;
  private final Request.Body body;

  /**
   * Writes the multipart part to the given output stream. The part is written in the following
   * format:
   *
   * <pre>{@code
   * headers
   *
   * body content
   * }</pre>
   *
   * @param outputStream {@inheritDoc}
   * @throws IOException {@inheritDoc}
   */
  @Override
  public void writeTo(OutputStream outputStream) throws IOException {
    outputStream.write(headersToString().getBytes(StandardCharsets.UTF_8));

    if (body != null) {
      body.writeTo(outputStream);
    }
  }

  /**
   * Returns the content length of the multipart part, which is the sum of the content length of the
   * body and the length of the headers.
   *
   * @return the content length of the multipart part, or {@code -1} if the content length of the
   *     body is unknown
   */
  @Override
  public long contentLength() {
    var bodyLength = body != null ? body.contentLength() : 0;

    return bodyLength < 0 ? bodyLength : bodyLength + headersToString().length();
  }

  @Override
  public boolean isRepeatable() {
    return body == null || body.isRepeatable();
  }

  /**
   * Returns a string representation of the multipart part, which consists of the headers and the
   * body content.
   *
   * @return a string representation of the multipart part
   */
  @Override
  public String toString() {
    return headersToString() + Objects.requireNonNullElse(body, "");
  }

  private String headersToString() {
    return headers.entrySet().stream()
            .map(entry -> entry.getKey() + ": " + entry.getValue())
            .collect(Collectors.joining(CRLF))
        + CRLF
        + CRLF;
  }
}
