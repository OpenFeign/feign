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
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;

/** Metadata for a single multipart part. */
@Value
@RequiredArgsConstructor
@Accessors(fluent = true)
public class PartMetadata {
  /** Form field name; never {@code null}. */
  @NonNull String name;

  /** Content of the part. May be {@code null}. */
  Object content;

  /** Optional filename written into {@code Content-Disposition: ...; filename="<value>"}. */
  String filenameOverride;

  /** Optional media type written as {@code Content-Type: <value>}. */
  String contentTypeOverride;

  /**
   * Constructs a new {@link PartMetadata} with the given name and content, and no filename or
   * content type overrides.
   *
   * @param name form field name; must not be {@code null}
   * @param content content of the part; may be {@code null}
   */
  public PartMetadata(String name, Object content) {
    this(name, content, null, null);
  }

  /**
   * Returns the content of the part as an {@link Optional}.
   *
   * @return an {@link Optional} containing the content of the part, or an empty {@link Optional} if
   *     the content is {@code null}
   */
  public Optional<Object> content() {
    return Optional.ofNullable(content);
  }

  /**
   * Returns the filename override of the part as an {@link Optional}.
   *
   * @return an {@link Optional} containing the filename override of the part, or an empty {@link
   *     Optional} if the filename override is {@code null}
   */
  public Optional<String> filenameOverride() {
    return Optional.ofNullable(filenameOverride);
  }

  /**
   * Returns the content type override of the part as an {@link Optional}.
   *
   * @return an {@link Optional} containing the content type override of the part, or an empty
   *     {@link Optional} if the content type override is {@code null}
   */
  public Optional<String> contentTypeOverride() {
    return Optional.ofNullable(contentTypeOverride);
  }
}
