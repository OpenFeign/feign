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
import java.lang.reflect.Type;

/**
 * A {@link Decoder} that knows which responses it can handle.
 *
 * <p>Decoders implement this to declare their own applicability, so a {@link MultiDecoder} can
 * route each response to the right one without the call site having to wrap anything:
 *
 * <pre>
 * public class JacksonDecoder implements Decoder, PredicatedDecoder {
 *
 *   &#064;Override
 *   public boolean canDecode(Response response, Type type) {
 *     return Util.isJsonContentType(response);
 *   }
 * }
 * </pre>
 *
 * <p>{@link Decoder#decode(Response, Type) decode} remains the only abstract method, so this stays
 * a functional interface and a bare lambda is a decoder that accepts everything.
 *
 * <p>Decoders that wrap another decoder should forward {@code canDecode} to their delegate, so that
 * wrapping does not discard the delegate's applicability.
 *
 * @see MultiDecoder
 * @see DecoderPredicate
 */
@Experimental
@FunctionalInterface
public interface PredicatedDecoder extends Decoder {

  /**
   * Whether this decoder can handle the response. Defaults to accepting everything.
   *
   * <p>The response body must not be read here: it is a single-pass stream for most clients, so
   * consuming it would leave nothing for the decoder that is eventually chosen.
   *
   * @param response the response that would be decoded. Its body must not be read.
   * @param type the {@link java.lang.reflect.Method#getGenericReturnType() generic return type} the
   *     caller expects back
   * @return {@code true} if this decoder can decode the response, {@code false} otherwise
   */
  default boolean canDecode(Response response, Type type) {
    return true;
  }
}
