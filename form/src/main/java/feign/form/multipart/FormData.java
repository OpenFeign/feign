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

import java.util.Optional;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * A wrapper for multipart form data. It allows to specify content, content type and filename for a
 * multipart part. The content is required, while content type and filename are optional.
 *
 * @param <T> type of the content
 */
@Data
@RequiredArgsConstructor
@Accessors(fluent = true)
public class FormData<T> {
  private final T content;
  private String contentType;
  private String filename;

  /**
   * Gets the content of the multipart part.
   *
   * @return an {@link Optional} containing the content, or an empty {@link Optional} if the content
   *     is {@code null}.
   */
  public Optional<T> content() {
    return Optional.ofNullable(content);
  }

  /**
   * Gets the content type of the multipart part.
   *
   * @return an {@link Optional} containing the content type, or an empty {@link Optional} if the
   *     content type is {@code null}.
   */
  public Optional<String> contentType() {
    return Optional.ofNullable(contentType);
  }

  /**
   * Gets the filename of the multipart part.
   *
   * @return an {@link Optional} containing the filename, or an empty {@link Optional} if the
   *     filename is {@code null}.
   */
  public Optional<String> filename() {
    return Optional.ofNullable(filename);
  }
}
