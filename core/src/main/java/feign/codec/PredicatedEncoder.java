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
import java.lang.reflect.Type;

/**
 * An {@link Encoder} that knows which requests it can handle.
 *
 * <p>Encoders implement this to declare their own applicability, so a {@link MultiEncoder} can
 * route each request to the right one without the call site having to wrap anything:
 *
 * <pre>
 * public class JacksonEncoder implements Encoder, PredicatedEncoder {
 *
 *   &#064;Override
 *   public boolean canEncode(Object object, Type bodyType, RequestTemplate template) {
 *     return EncoderPredicate.jsonContentType().canEncode(object, bodyType, template);
 *   }
 * }
 * </pre>
 *
 * <p>{@link Encoder#encode(Object, Type, RequestTemplate) encode} remains the only abstract method,
 * so this stays a functional interface and a bare lambda is an encoder that accepts everything.
 *
 * <p>Encoders that wrap another encoder should forward {@code canEncode} to their delegate, so that
 * wrapping does not discard the delegate's applicability.
 *
 * @see MultiEncoder
 * @see EncoderPredicate
 */
@Experimental
@FunctionalInterface
public interface PredicatedEncoder extends Encoder {

  /**
   * Whether this encoder can handle the request. Defaults to accepting everything.
   *
   * @param object what to encode as the request body
   * @param bodyType the type the object should be encoded as. {@link Encoder#MAP_STRING_WILDCARD}
   *     indicates form encoding.
   * @param template the request template to populate
   * @return {@code true} if this encoder can encode the request, {@code false} otherwise
   */
  default boolean canEncode(Object object, Type bodyType, RequestTemplate template) {
    return true;
  }
}
