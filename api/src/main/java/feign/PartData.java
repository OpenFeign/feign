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

/**
 * Runtime data for one multipart part, carrying the actual argument value and its header templates.
 *
 * @apiNote instances are produced by the multipart request-template factory and passed to the
 *     encoder for header resolution and wire-format serialization. The encoder is responsible for
 *     resolving any {@code {name}} placeholders in the header values using the {@link
 *     RequestTemplate}'s variable map.
 */
public class PartData {
  private final Type type;
  private final Object value;
  private final Map<String, Collection<String>> headers;
  private final boolean explode;

  /**
   * Creates runtime data for one multipart part.
   *
   * @param type the declared method parameter type of this part
   * @param value the runtime argument value for this part
   * @param headers part header templates, keyed by header name; values may contain {@code {name}}
   *     placeholders to be resolved by the encoder
   * @param explode whether arrays and iterable values should be expanded into repeated parts
   */
  public PartData(
      Type type, Object value, Map<String, Collection<String>> headers, boolean explode) {
    this.type = Objects.requireNonNull(type, "type must not be null");
    this.value = value;
    this.headers = Objects.requireNonNull(headers, "headers must not be null");
    this.explode = explode;
  }

  /**
   * Returns the declared method parameter type of this part.
   *
   * @return the declared method parameter type of this part
   */
  public Type type() {
    return type;
  }

  /**
   * Returns the runtime argument value for this part.
   *
   * @return the runtime argument value for this part, possibly {@code null}
   */
  public Object value() {
    return value;
  }

  /**
   * Returns the part header templates, keyed by header name. Values may contain {@code {name}}
   * placeholders to be resolved by the encoder.
   *
   * @return the part header templates, keyed by header name
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
    return explode;
  }

  /**
   * {@inheritDoc}
   *
   * @param object {@inheritDoc}
   * @return {@inheritDoc}
   */
  @Override
  public boolean equals(Object object) {
    if (!(object instanceof PartData)) return false;
    PartData partData = (PartData) object;
    return explode == partData.explode
        && Objects.equals(type, partData.type)
        && Objects.equals(value, partData.value)
        && Objects.equals(headers, partData.headers);
  }

  /**
   * {@inheritDoc}
   *
   * @return {@inheritDoc}
   */
  @Override
  public int hashCode() {
    return Objects.hash(type, value, headers, explode);
  }

  /**
   * {@inheritDoc}
   *
   * @return {@inheritDoc}
   */
  @Override
  public String toString() {
    return "PartData{"
        + "type="
        + type
        + ", value="
        + value
        + ", headers="
        + headers
        + ", explode="
        + explode
        + '}';
  }
}
