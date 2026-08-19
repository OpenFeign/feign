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
import feign.RequestTemplate;
import feign.Util;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * Decides whether a request can be handled by an {@link Encoder}.
 *
 * <p>Predicates receive the same three arguments as {@link Encoder#encode(Object, Type,
 * RequestTemplate)}, so they can discriminate on the body, on its declared type, or on anything
 * already present in the template such as the {@code Content-Type} header.
 *
 * @see PredicatedEncoder
 * @see MultiEncoder
 */
@Experimental
@FunctionalInterface
public interface EncoderPredicate {

  /**
   * Whether the encoder this predicate guards can handle the request.
   *
   * @param object what would be encoded as the request body
   * @param bodyType the type the object would be encoded as. {@link Encoder#MAP_STRING_WILDCARD}
   *     indicates form encoding.
   * @param template the request template that would be populated
   * @return {@code true} if the request can be encoded, {@code false} otherwise
   */
  boolean canEncode(Object object, Type bodyType, RequestTemplate template);

  /** Matches requests whose {@code Content-Type} header denotes JSON. */
  static EncoderPredicate jsonContentType() {
    return (object, bodyType, template) -> Util.isJsonContentType(template);
  }

  /** Matches requests whose {@code Content-Type} header denotes XML. */
  static EncoderPredicate xmlContentType() {
    return (object, bodyType, template) -> Util.isXmlContentType(template);
  }

  /**
   * Matches requests whose {@code Content-Type} header starts with the given media type, ignoring
   * case and any parameters such as {@code ;charset=utf-8}.
   */
  static EncoderPredicate contentType(String mediaType) {
    Objects.requireNonNull(mediaType, "mediaType cannot be null");
    return (object, bodyType, template) -> Util.hasContentType(template, mediaType);
  }

  /** Matches requests carrying no body. */
  static EncoderPredicate emptyBody() {
    return (object, bodyType, template) -> object == null;
  }

  /** Matches requests whose declared body type is exactly the given type. */
  static EncoderPredicate bodyType(Type type) {
    Objects.requireNonNull(type, "type cannot be null");
    return (object, bodyType, template) -> type.equals(bodyType);
  }

  /** Matches form-encoded requests, as signalled by {@link Encoder#MAP_STRING_WILDCARD}. */
  static EncoderPredicate formEncoded() {
    return (object, bodyType, template) -> Encoder.MAP_STRING_WILDCARD.equals(bodyType);
  }

  default EncoderPredicate and(EncoderPredicate other) {
    Objects.requireNonNull(other, "other cannot be null");
    return (object, bodyType, template) ->
        canEncode(object, bodyType, template) && other.canEncode(object, bodyType, template);
  }

  default EncoderPredicate or(EncoderPredicate other) {
    Objects.requireNonNull(other, "other cannot be null");
    return (object, bodyType, template) ->
        canEncode(object, bodyType, template) || other.canEncode(object, bodyType, template);
  }

  default EncoderPredicate negate() {
    return (object, bodyType, template) -> !canEncode(object, bodyType, template);
  }
}
