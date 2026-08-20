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
 * public class JacksonDecoder implements PredicatedDecoder {
 *
 *   &#064;Override
 *   public boolean canDecode(Response response, Type type) {
 *     return Util.isJsonContentType(response);
 *   }
 *
 *   &#064;Override
 *   public Object decode(Response response, Type type) throws IOException {
 *     // ...
 *   }
 * }
 * </pre>
 *
 * <p>{@code canDecode} is deliberately abstract: a decoder that says nothing about what it handles
 * would claim every response, which is almost never what its author meant. Use {@link
 * #of(DecoderPredicate, Decoder)} to give an existing decoder a predicate instead of implementing
 * this on it, and {@link DecoderPredicate} &mdash; which is a {@code @FunctionalInterface} &mdash;
 * to write that predicate as a lambda.
 *
 * <p>Decoders that wrap another decoder should forward {@code canDecode} to their delegate, so that
 * wrapping does not discard the delegate's applicability.
 *
 * @see MultiDecoder
 * @see DecoderPredicate
 */
@Experimental
public interface PredicatedDecoder extends Decoder {

  /**
   * Pairs any decoder with a predicate, for decoders that do not declare themselves, including ones
   * you do not control. The predicate is the whole answer: whatever the decoder may declare about
   * itself is replaced, so this can widen a decoder as well as narrow it. Use {@link
   * #narrowing(DecoderPredicate, Decoder)} to keep the decoder's own declaration.
   *
   * <p>A decoder paired with {@link DecoderPredicate#any()} accepts everything, which is how a
   * {@link MultiDecoder} is given a default:
   *
   * <pre>
   * Feign.builder()
   *     .decoders(
   *         new JacksonDecoder(),
   *         PredicatedDecoder.of(DecoderPredicate.any(), new DefaultDecoder()));
   * </pre>
   *
   * @param predicate decides whether the decoder handles a response
   * @param decoder the decoder to delegate to
   */
  static PredicatedDecoder of(DecoderPredicate predicate, Decoder decoder) {
    return new PairedDecoder(predicate, decoder);
  }

  /**
   * Narrows a decoder that already declares itself, by requiring both the given predicate and the
   * decoder's own {@code canDecode} to accept the response:
   *
   * <pre>
   * PredicatedDecoder.narrowing(
   *     DecoderPredicate.status(200), new JacksonDecoder());
   * </pre>
   *
   * <p>A decoder that does not implement {@link PredicatedDecoder} declares nothing to narrow, so
   * this behaves like {@link #of(DecoderPredicate, Decoder)}.
   *
   * @param predicate narrows what the decoder handles
   * @param decoder the decoder to delegate to
   */
  static PredicatedDecoder narrowing(DecoderPredicate predicate, Decoder decoder) {
    return new PairedDecoder(PairedDecoder.narrow(predicate, decoder), decoder);
  }

  /**
   * Whether this decoder can handle the response.
   *
   * <p>The response body must not be read here: it is a single-pass stream for most clients, so
   * consuming it would leave nothing for the decoder that is eventually chosen.
   *
   * @param response the response that would be decoded. Its body must not be read.
   * @param type the {@link java.lang.reflect.Method#getGenericReturnType() generic return type} the
   *     caller expects back
   * @return {@code true} if this decoder can decode the response, {@code false} otherwise
   */
  boolean canDecode(Response response, Type type);
}
