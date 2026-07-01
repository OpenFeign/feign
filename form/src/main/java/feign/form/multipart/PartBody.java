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
import feign.RequestTemplate;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** A {@link Request.Body} implementation that represents a single part of a multipart form body. */
@Data
@RequiredArgsConstructor
@Accessors(fluent = true)
public class PartBody implements Request.Body {
  private static final String CRLF = "\r\n";

  @NonNull private final Map<String, Collection<String>> headers;
  private final Request.Body body;

  /**
   * Creates a new {@link PartBody} from the given {@link RequestTemplate}.
   *
   * @param template the {@link RequestTemplate} to create the {@link PartBody} from
   * @return a new {@link PartBody} instance
   */
  static PartBody from(@NonNull RequestTemplate template) {
    return new PartBody(template.headers(), template.requestBody().orElse(null));
  }

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
    var contentLength = body != null ? body.contentLength() : 0;

    return contentLength < 0 ? contentLength : contentLength + headersToString().length();
  }

  /**
   * Returns the body of the multipart part, if present.
   *
   * @return an {@link Optional} containing the body of the multipart part, or an empty {@link
   *     Optional} if the body is not present
   */
  public Optional<Request.Body> body() {
    return Optional.ofNullable(body);
  }

  /**
   * {@inheritDoc}
   *
   * @return {@inheritDoc}
   */
  @Override
  public String toString() {
    return headersToString() + Objects.requireNonNullElse(body, "");
  }

  private String headersToString() {
    return headers.entrySet().stream()
        .flatMap(entry -> entry.getValue().stream().map(value -> entry.getKey() + ": " + value))
        .collect(Collectors.joining(CRLF, "", CRLF + CRLF));
  }
}
