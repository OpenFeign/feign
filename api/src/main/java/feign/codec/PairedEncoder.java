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
import java.lang.reflect.Type;
import java.util.Objects;

/** An encoder that does not declare itself, guarded by a predicate supplied at the call site. */
final class PairedEncoder implements PredicatedEncoder {

  private final EncoderPredicate predicate;

  private final Encoder encoder;

  PairedEncoder(EncoderPredicate predicate, Encoder encoder) {
    this.predicate = Objects.requireNonNull(predicate, "predicate cannot be null");
    this.encoder = Objects.requireNonNull(encoder, "encoder cannot be null");
  }

  @Override
  public boolean canEncode(Object object, Type bodyType, RequestTemplate template) {
    return predicate.canEncode(object, bodyType, template);
  }

  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template)
      throws EncodeException {
    encoder.encode(object, bodyType, template);
  }

  @Override
  public String toString() {
    return describe(encoder) + " when " + predicate;
  }

  /** Requires both the predicate and, when the encoder declares one, its own applicability. */
  static EncoderPredicate narrow(EncoderPredicate predicate, Encoder encoder) {
    Objects.requireNonNull(predicate, "predicate cannot be null");
    Objects.requireNonNull(encoder, "encoder cannot be null");
    if (!(encoder instanceof PredicatedEncoder)) {
      return predicate;
    }
    if (encoder instanceof PairedEncoder) {
      return predicate.and(((PairedEncoder) encoder).predicate);
    }
    PredicatedEncoder predicated = (PredicatedEncoder) encoder;
    return predicate.and(
        EncoderPredicate.describedAs(describe(encoder) + " accepts it", predicated::canEncode));
  }

  /** The encoder's own {@code toString} when it has one, its class name otherwise. */
  static String describe(Encoder encoder) {
    Class<?> type = encoder.getClass();
    try {
      if (type.getMethod("toString").getDeclaringClass() != Object.class) {
        return encoder.toString();
      }
    } catch (NoSuchMethodException ignored) {
      // cannot happen, every class has toString
    }
    return type.getSimpleName().isEmpty() ? type.getName() : type.getSimpleName();
  }
}
