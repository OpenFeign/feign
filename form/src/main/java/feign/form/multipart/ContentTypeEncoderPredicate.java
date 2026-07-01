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

import feign.RequestTemplate;
import java.lang.reflect.Type;
import java.util.List;
import lombok.NonNull;

/**
 * An {@link EncoderPredicate} that checks if the request template has a {@code Content-Type} header
 * that starts with the specified content type.
 */
class ContentTypeEncoderPredicate implements EncoderPredicate {
  private final String contentType;

  /**
   * Creates a new instance of {@link ContentTypeEncoderPredicate} with the specified content type.
   *
   * @param contentType the content type to check for in the request template's {@code Content-Type}
   *     header
   */
  ContentTypeEncoderPredicate(@NonNull String contentType) {
    this.contentType = normalizeHeader(contentType);
  }

  private static String normalizeHeader(String header) {
    return header.trim().toLowerCase();
  }

  /**
   * Tests if the request template has a {@code Content-Type} header that starts with the specified
   * content type.
   *
   * @param object {@inheritDoc}
   * @param bodyType {@inheritDoc}
   * @param template {@inheritDoc}
   * @return {@code true} if the request template has a {@code Content-Type} header that starts with
   *     the specified content type, {@code false} otherwise
   */
  @Override
  public boolean test(Object object, Type bodyType, RequestTemplate template) {
    return template.headers().getOrDefault("Content-Type", List.of()).stream()
        .anyMatch(header -> normalizeHeader(header).startsWith(contentType));
  }
}
