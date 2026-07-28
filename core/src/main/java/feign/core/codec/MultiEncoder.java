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
package feign.core.codec;

import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * An encoder that delegates to a list of encoders, using the first one that can encode the given
 * object.
 *
 * @since 14
 */
public class MultiEncoder implements Encoder {
  private final List<Encoder> delegates;

  /**
   * Creates a delegating encoder that will try each of the provided encoders in order until one
   * returns {@code true} from {@link #encode(Object, Type, RequestTemplate)}.
   *
   * @param encoders the encoders to delegate to
   * @return a delegating encoder
   * @since 14
   */
  static Encoder of(Encoder... encoders) {
    return of(Arrays.asList(encoders));
  }

  /**
   * Creates a delegating encoder that will try each of the provided encoders in order until one
   * returns {@code true} from {@link #encode(Object, Type, RequestTemplate)}.
   *
   * @param encoders the encoders to delegate to
   * @return a delegating encoder
   * @since 14
   */
  static Encoder of(List<Encoder> encoders) {
    return new MultiEncoder(encoders);
  }

  /**
   * Creates a new {@link MultiEncoder} with the given list of delegates.
   *
   * @param delegates the list of delegates to use for encoding. Both list and its elements must not
   *     be {@code null}.
   */
  private MultiEncoder(List<Encoder> delegates) {
    this.delegates = Objects.requireNonNull(delegates, "delegates cannot be null");
  }

  /**
   * Encodes the given object using the first delegate that can encode it. If no delegate can encode
   * the object, an {@link EncodeException} is thrown.
   *
   * @param object {@inheritDoc}
   * @param bodyType {@inheritDoc}
   * @param template {@inheritDoc}
   * @throws EncodeException {@inheritDoc}
   */
  @Override
  public boolean encode(Object object, Type bodyType, RequestTemplate template)
      throws EncodeException {
    return delegates.stream()
        .map(encoder -> encoder.encode(object, bodyType, template))
        .filter(Boolean::booleanValue)
        .findFirst()
        .orElse(false);
  }

  /**
   * {@inheritDoc}
   *
   * @return {@inheritDoc}
   */
  @Override
  public String toString() {
    return "DelegatingEncoder{" + "delegates=" + delegates + '}';
  }
}
