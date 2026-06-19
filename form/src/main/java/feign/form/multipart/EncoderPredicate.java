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
import feign.Util;
import java.lang.reflect.Type;
import java.util.List;
import lombok.NonNull;

/** A predicate for determining whether a given object can be encoded. */
@FunctionalInterface
public interface EncoderPredicate {
  /**
   * Creates an {@link EncoderPredicate} that checks if the request template contains a {@code
   * Content-Type} header that starts with the given content type.
   *
   * @param contentType the case-insensitive content type to check for in the request template's
   *     {@code Content-Type} header
   * @return an {@link EncoderPredicate} that checks for the specified content type in the request
   *     template's headers
   */
  static EncoderPredicate forContentType(@NonNull String contentType) {
    var expectedContentType = normalizeHeader(contentType);

    return (object, bodyType, template) ->
        template.headers().getOrDefault(Util.CONTENT_TYPE, List.of()).stream()
            .anyMatch(header -> normalizeHeader(header).startsWith(expectedContentType));
  }

  private static String normalizeHeader(String header) {
    return header.trim().toLowerCase();
  }

  /**
   * Determines whether the given object can be encoded.
   *
   * @param object the object to be encoded
   * @param bodyType the type of the object to be encoded
   * @param template the request template containing the headers and other information about the
   *     request
   * @return {@code true} if the object can be encoded, {@code false} otherwise
   */
  boolean test(Object object, Type bodyType, RequestTemplate template);
}
