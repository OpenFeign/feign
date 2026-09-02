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

import feign.FeignException;
import feign.Response;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Objects;

/** A decoder that does not declare itself, guarded by a predicate supplied at the call site. */
final class PairedDecoder implements PredicatedDecoder {

  private final DecoderPredicate predicate;

  private final Decoder decoder;

  PairedDecoder(DecoderPredicate predicate, Decoder decoder) {
    this.predicate = Objects.requireNonNull(predicate, "predicate cannot be null");
    this.decoder = Objects.requireNonNull(decoder, "decoder cannot be null");
  }

  @Override
  public boolean canDecode(Response response, Type type) {
    return predicate.canDecode(response, type);
  }

  @Override
  public Object decode(Response response, Type type)
      throws IOException, DecodeException, FeignException {
    return decoder.decode(response, type);
  }

  @Override
  public String toString() {
    return describe(decoder) + " when " + predicate;
  }

  /** Requires both the predicate and, when the decoder declares one, its own applicability. */
  static DecoderPredicate narrow(DecoderPredicate predicate, Decoder decoder) {
    Objects.requireNonNull(predicate, "predicate cannot be null");
    Objects.requireNonNull(decoder, "decoder cannot be null");
    if (!(decoder instanceof PredicatedDecoder)) {
      return predicate;
    }
    if (decoder instanceof PairedDecoder) {
      return predicate.and(((PairedDecoder) decoder).predicate);
    }
    PredicatedDecoder predicated = (PredicatedDecoder) decoder;
    return predicate.and(
        DecoderPredicate.describedAs(describe(decoder) + " accepts it", predicated::canDecode));
  }

  /** The decoder's own {@code toString} when it has one, its class name otherwise. */
  static String describe(Decoder decoder) {
    Class<?> type = decoder.getClass();
    try {
      if (type.getMethod("toString").getDeclaringClass() != Object.class) {
        return decoder.toString();
      }
    } catch (NoSuchMethodException ignored) {
      // cannot happen, every class has toString
    }
    return type.getSimpleName().isEmpty() ? type.getName() : type.getSimpleName();
  }
}
