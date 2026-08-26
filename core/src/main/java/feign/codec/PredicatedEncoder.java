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
 * public class JacksonEncoder implements PredicatedEncoder {
 *
 *   &#064;Override
 *   public boolean canEncode(Object object, Type bodyType, RequestTemplate template) {
 *     return Util.isJsonContentType(template);
 *   }
 *
 *   &#064;Override
 *   public void encode(Object object, Type bodyType, RequestTemplate template) {
 *     // ...
 *   }
 * }
 * </pre>
 *
 * <p>{@code canEncode} is deliberately abstract: an encoder that says nothing about what it handles
 * would claim every request, which is almost never what its author meant. Use {@link
 * #of(EncoderPredicate, Encoder)} to give an existing encoder a predicate instead of implementing
 * this on it, and {@link EncoderPredicate} &mdash; which is a {@code @FunctionalInterface} &mdash;
 * to write that predicate as a lambda.
 *
 * <p>Encoders that wrap another encoder should forward {@code canEncode} to their delegate, so that
 * wrapping does not discard the delegate's applicability.
 *
 * @see MultiEncoder
 * @see EncoderPredicate
 */
@Experimental
public interface PredicatedEncoder extends Encoder {

  /**
   * Pairs any encoder with a predicate, for encoders that do not declare themselves, including ones
   * you do not control. The predicate is the whole answer: whatever the encoder may declare about
   * itself is replaced, so this can widen an encoder as well as narrow it. Use {@link
   * #narrowing(EncoderPredicate, Encoder)} to keep the encoder's own declaration.
   *
   * <p>An encoder paired with {@link EncoderPredicate#any()} accepts everything, which is how a
   * {@link MultiEncoder} is given a default:
   *
   * <pre>
   * Feign.builder()
   *     .encoders(
   *         new JacksonEncoder(),
   *         PredicatedEncoder.of(EncoderPredicate.any(), new Encoder.Default()));
   * </pre>
   *
   * <p>The predicate is used <em>instead of</em> the encoder's own, not in addition to it. {@link
   * MultiEncoder.Builder#add(EncoderPredicate, Encoder)} is the same thing at the call site.
   *
   * @param predicate decides whether the encoder handles a request
   * @param encoder the encoder to delegate to
   */
  static PredicatedEncoder of(EncoderPredicate predicate, Encoder encoder) {
    return new PairedEncoder(predicate, encoder);
  }

  /**
   * Narrows an encoder that already declares itself, by requiring both the given predicate and the
   * encoder's own {@code canEncode} to accept the request:
   *
   * <pre>
   * PredicatedEncoder.narrowing(
   *     EncoderPredicate.contentType("application/vnd.acme+json"), new GsonEncoder());
   * </pre>
   *
   * <p>An encoder that does not implement {@link PredicatedEncoder} declares nothing to narrow, so
   * this behaves like {@link #of(EncoderPredicate, Encoder)}.
   *
   * <p>The predicate is used <em>in addition to</em> the encoder's own, not instead of it. {@link
   * MultiEncoder.Builder#narrow(EncoderPredicate, Encoder)} is the same thing at the call site.
   *
   * @param predicate narrows what the encoder handles
   * @param encoder the encoder to delegate to
   */
  static PredicatedEncoder narrowing(EncoderPredicate predicate, Encoder encoder) {
    return new PairedEncoder(PairedEncoder.narrow(predicate, encoder), encoder);
  }

  /**
   * Whether this encoder can handle the request.
   *
   * @param object what to encode as the request body
   * @param bodyType the type the object should be encoded as. {@link Encoder#MAP_STRING_WILDCARD}
   *     indicates form encoding.
   * @param template the request template to populate
   * @return {@code true} if this encoder can encode the request, {@code false} otherwise
   */
  boolean canEncode(Object object, Type bodyType, RequestTemplate template);
}
