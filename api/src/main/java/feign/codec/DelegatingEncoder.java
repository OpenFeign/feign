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
import java.util.List;
import java.util.Objects;

/**
 * An encoder that delegates to a list of encoders, using the first one that can encode the given
 * object.
 *
 * @since 14
 */
public class DelegatingEncoder implements Encoder {
  private final List<Encoder> delegates;

  /**
   * Creates a new {@link DelegatingEncoder} with the given list of delegates.
   *
   * @param delegates the list of delegates to use for encoding. Both list and its elements must not
   *     be {@code null}.
   */
  public DelegatingEncoder(List<Encoder> delegates) {
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
  public void encode(Object object, Type bodyType, RequestTemplate template)
      throws EncodeException {
    delegates.stream()
        .filter(encoder -> encoder.canEncode(object, bodyType, template))
        .findFirst()
        .orElseThrow(
            () ->
                new EncodeException(
                    "No suitable encoder found for object encoding: "
                        + object
                        + ", encoders: "
                        + delegates))
        .encode(object, bodyType, template);
  }

  /**
   * Checks if any of the delegates can encode the given object.
   *
   * @param object {@inheritDoc}
   * @param bodyType {@inheritDoc}
   * @param template {@inheritDoc}
   * @return {@inheritDoc}
   */
  @Override
  public boolean canEncode(Object object, Type bodyType, RequestTemplate template) {
    return delegates.stream().anyMatch(delegate -> delegate.canEncode(object, bodyType, template));
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
