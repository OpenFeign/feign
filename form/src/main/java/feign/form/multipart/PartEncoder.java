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
package feign.form.multipart;

import feign.Request;
import feign.codec.EncodeException;

/**
 * A strategy that encodes a {@link PartMetadata} into a {@link Request.Body}. It's recommended to
 * use {@link AbstractPartEncoder} as a base class for custom implementations.
 *
 * @see AbstractPartEncoder
 */
@FunctionalInterface
public interface PartEncoder {
  /**
   * Encodes the given {@link PartMetadata} into a {@link Request.Body}.
   *
   * @param partMetadata the metadata of the part to encode
   * @return the encoded part as a {@link Request.Body}
   * @throws EncodeException if encoding fails
   */
  Request.Body encode(PartMetadata partMetadata) throws EncodeException;

  /**
   * Determines whether this encoder supports encoding the given {@link PartMetadata}. By default,
   * it returns {@code true}.
   *
   * @param partMetadata the metadata of the part to check for support
   * @return {@code true} if this encoder supports encoding the given part metadata, {@code false}
   *     otherwise
   */
  default boolean supports(PartMetadata partMetadata) {
    return true;
  }
}
