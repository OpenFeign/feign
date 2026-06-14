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

/** A {@link PartEncoder} that encodes a part with a byte array content. */
public class ByteArrayPartEncoder extends AbstractPartEncoder {
  /**
   * Creates a {@link Request.Body} from a given byte array part content.
   *
   * @param partMetadata {@inheritDoc}
   * @return {@inheritDoc}
   */
  @Override
  public Request.Body encode(PartMetadata partMetadata) {
    return new Part(
        createHeaders(
            partMetadata.name(),
            partMetadata.filenameOverride().orElse(null),
            partMetadata.contentTypeOverride().orElse(null)),
        Request.Body.of((byte[]) partMetadata.content().orElseThrow()));
  }

  /**
   * {@inheritDoc}
   *
   * @param partMetadata {@inheritDoc}
   * @return {@code true} if the part content is a byte array, {@code false} otherwise
   */
  @Override
  public boolean supports(PartMetadata partMetadata) {
    return partMetadata.content().filter(byte[].class::isInstance).isPresent();
  }
}
