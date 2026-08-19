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
package feign.codec;

import feign.Experimental;
import feign.Response;
import feign.Util;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Objects;

/**
 * Decides whether a response can be handled by a {@link Decoder}.
 *
 * <p>Predicates receive the same two arguments as {@link Decoder#decode(Response, Type)}, so they
 * can discriminate on the response status, on anything in its headers such as the {@code
 * Content-Type}, or on the type the caller expects back.
 *
 * <p><strong>Predicates must not read the response body.</strong> The body is a single-pass stream
 * for most clients, so consuming it here would leave nothing for the decoder that is eventually
 * chosen.
 *
 * @see PredicatedDecoder
 * @see MultiDecoder
 */
@Experimental
@FunctionalInterface
public interface DecoderPredicate {

  /**
   * Whether the decoder this predicate guards can handle the response.
   *
   * @param response the response that would be decoded. Its body must not be read.
   * @param type the {@link java.lang.reflect.Method#getGenericReturnType() generic return type} the
   *     caller expects back
   * @return {@code true} if the response can be decoded, {@code false} otherwise
   */
  boolean canDecode(Response response, Type type);

  /** Matches responses whose {@code Content-Type} header denotes JSON. */
  static DecoderPredicate jsonContentType() {
    return (response, type) -> Util.isJsonContentType(response);
  }

  /** Matches responses whose {@code Content-Type} header denotes XML. */
  static DecoderPredicate xmlContentType() {
    return (response, type) -> Util.isXmlContentType(response);
  }

  /**
   * Matches responses whose {@code Content-Type} header starts with the given media type, ignoring
   * case and any parameters such as {@code ;charset=utf-8}.
   */
  static DecoderPredicate contentType(String mediaType) {
    Objects.requireNonNull(mediaType, "mediaType cannot be null");
    return (response, type) -> Util.hasContentType(response, mediaType);
  }

  /** Matches responses carrying no body, such as a {@code 204 No Content}. */
  static DecoderPredicate emptyBody() {
    return (response, type) ->
        response.body() == null
            || (response.body().length() != null && response.body().length() == 0);
  }

  /** Matches responses whose status is one of the given codes. */
  static DecoderPredicate status(int... statuses) {
    int[] accepted = Arrays.copyOf(statuses, statuses.length);
    Arrays.sort(accepted);
    return (response, type) -> Arrays.binarySearch(accepted, response.status()) >= 0;
  }

  /** Matches responses the caller expects to come back as exactly the given type. */
  static DecoderPredicate returnType(Type expected) {
    Objects.requireNonNull(expected, "expected cannot be null");
    return (response, type) -> expected.equals(type);
  }

  default DecoderPredicate and(DecoderPredicate other) {
    Objects.requireNonNull(other, "other cannot be null");
    return (response, type) -> canDecode(response, type) && other.canDecode(response, type);
  }

  default DecoderPredicate or(DecoderPredicate other) {
    Objects.requireNonNull(other, "other cannot be null");
    return (response, type) -> canDecode(response, type) || other.canDecode(response, type);
  }

  default DecoderPredicate negate() {
    return (response, type) -> !canDecode(response, type);
  }
}
