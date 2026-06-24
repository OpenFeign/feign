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
package feign;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/** Declaration-time metadata for one {@link Part} method parameter. */
public class PartMetadata {
  private final int index;
  private final Type type;
  private final Map<String, Collection<String>> headers;
  private final boolean unwrap;

  /**
   * Creates metadata for one multipart part parameter.
   *
   * @param index method parameter index
   * @param type method parameter type
   * @param headers part header templates declared by {@link Part}, keyed by header name; values may
   *     contain {@code {name}} placeholders to be resolved by the encoder
   * @param unwrap whether arrays and iterable values should be expanded into repeated parts
   */
  public PartMetadata(
      int index, Type type, Map<String, Collection<String>> headers, boolean unwrap) {
    this.index = index;
    this.type = Objects.requireNonNull(type, "type must not be null");
    this.headers = Objects.requireNonNull(headers, "headers must not be null");
    this.unwrap = unwrap;
  }

  /**
   * Returns the method parameter index of this part.
   *
   * @return the method parameter index of this part
   */
  public int index() {
    return index;
  }

  /**
   * Returns the method parameter type of this part.
   *
   * @return the method parameter type of this part
   */
  public Type type() {
    return type;
  }

  /**
   * Returns the part header templates declared by {@link Part}, keyed by header name. Values may
   * contain {@code {name}} placeholders to be resolved by the encoder.
   *
   * @return the part header templates declared by {@link Part}, keyed by header name
   */
  public Map<String, Collection<String>> headers() {
    return headers;
  }

  /**
   * Returns whether arrays and iterable values should be expanded into repeated parts.
   *
   * @return {@code true} if arrays and iterable values should be expanded into repeated parts,
   *     {@code false} otherwise
   */
  public boolean unwrap() {
    return unwrap;
  }

  /**
   * {@inheritDoc}
   *
   * @param object {@inheritDoc}
   * @return {@inheritDoc}
   */
  @Override
  public boolean equals(Object object) {
    if (!(object instanceof PartMetadata)) return false;
    PartMetadata that = (PartMetadata) object;
    return index == that.index
        && unwrap == that.unwrap
        && Objects.equals(type, that.type)
        && Objects.equals(headers, that.headers);
  }

  /**
   * {@inheritDoc}
   *
   * @return {@inheritDoc}
   */
  @Override
  public int hashCode() {
    return Objects.hash(index, type, headers, unwrap);
  }

  /**
   * {@inheritDoc}
   *
   * @return {@inheritDoc}
   */
  @Override
  public String toString() {
    return "PartMetadata{"
        + "index="
        + index
        + ", type="
        + type
        + ", headers="
        + headers
        + ", unwrap="
        + unwrap
        + '}';
  }
}
