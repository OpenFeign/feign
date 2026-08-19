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

import feign.RequestTemplate;
import feign.Util;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * Pairs an {@link EncoderPredicate} with the {@link Encoder} it guards, so that a {@link
 * MultiEncoder} can pick the right encoder per request.
 *
 * <p>Encoding through a {@code PredicatedEncoder} directly is allowed but strict: a request its
 * predicate rejects raises {@link EncodeException} rather than silently doing nothing. Inside a
 * {@link MultiEncoder} a rejected request simply moves on to the next candidate.
 *
 * <pre>
 * Feign.builder()
 *     .encoder(
 *         new DefaultEncoder(),
 *         PredicatedEncoder.forJsonContentType(new JacksonEncoder()),
 *         PredicatedEncoder.forXmlContentType(new JAXBEncoder()))
 * </pre>
 */
public class PredicatedEncoder implements Encoder {

  private final EncoderPredicate predicate;

  private final Encoder delegate;

  public PredicatedEncoder(EncoderPredicate predicate, Encoder delegate) {
    this.predicate = Objects.requireNonNull(predicate, "predicate cannot be null");
    this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
  }

  /** Restricts the delegate to requests whose {@code Content-Type} header denotes JSON. */
  public static PredicatedEncoder forJsonContentType(Encoder delegate) {
    return new PredicatedEncoder(
        (object, bodyType, template) -> Util.isJsonContentType(template), delegate);
  }

  /** Restricts the delegate to requests whose {@code Content-Type} header denotes XML. */
  public static PredicatedEncoder forXmlContentType(Encoder delegate) {
    return new PredicatedEncoder(
        (object, bodyType, template) -> Util.isXmlContentType(template), delegate);
  }

  /** Restricts the delegate to requests carrying no body. */
  public static PredicatedEncoder forEmptyBody(Encoder delegate) {
    return new PredicatedEncoder((object, bodyType, template) -> object == null, delegate);
  }

  /**
   * Whether the guarded encoder accepts this request.
   *
   * @param object what to encode as the request body
   * @param bodyType the type the object should be encoded as
   * @param template the request template to populate
   * @return {@code true} if the delegate should handle this request
   */
  public boolean canEncode(Object object, Type bodyType, RequestTemplate template) {
    return predicate.test(object, bodyType, template);
  }

  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template)
      throws EncodeException {
    if (!canEncode(object, bodyType, template)) {
      throw new EncodeException(
          "Predicate of " + this + " rejected the request, so " + delegate + " was not invoked");
    }
    delegate.encode(object, bodyType, template);
  }

  @Override
  public String toString() {
    return "PredicatedEncoder{predicate=" + predicate + ", delegate=" + delegate + '}';
  }
}
