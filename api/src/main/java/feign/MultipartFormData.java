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

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A container for a list of resolved multipart parts and the invocation-time parameter map, passed
 * to the encoder for header resolution and wire-format serialization.
 */
public class MultipartFormData {
  private final List<PartData> parts;
  private final Map<String, Object> variables;

  /**
   * Creates a multipart form body from a list of parts and a parameter map.
   *
   * @param parts list of multipart parts
   * @param variables invocation-time parameter name to value map, used by the encoder to resolve
   *     {@code {name}} placeholders in part headers
   */
  public MultipartFormData(List<PartData> parts, Map<String, Object> variables) {
    this.parts = Objects.requireNonNull(parts, "parts must not be null");
    this.variables = Objects.requireNonNull(variables, "variables must not be null");
  }

  /**
   * Returns the list of multipart parts.
   *
   * @return the list of multipart parts
   */
  public List<PartData> parts() {
    return parts;
  }

  /**
   * Returns the invocation-time parameter name to value map, used by the encoder to resolve {@code
   * {name}} placeholders in part headers.
   *
   * @return the invocation-time parameter name to value map
   */
  public Map<String, Object> variables() {
    return variables;
  }

  /**
   * {@inheritDoc}
   *
   * @param object {@inheritDoc}
   * @return {@inheritDoc}
   */
  @Override
  public boolean equals(Object object) {
    if (!(object instanceof MultipartFormData)) return false;
    MultipartFormData that = (MultipartFormData) object;
    return Objects.equals(parts, that.parts) && Objects.equals(variables, that.variables);
  }

  /**
   * {@inheritDoc}
   *
   * @return {@inheritDoc}
   */
  @Override
  public int hashCode() {
    return Objects.hash(parts, variables);
  }

  /**
   * {@inheritDoc}
   *
   * @return {@inheritDoc}
   */
  @Override
  public String toString() {
    return "MultipartFormData{" + "parts=" + parts + ", variables=" + variables + '}';
  }
}
