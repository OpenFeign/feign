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
import java.util.Collection;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * A chain of {@link PartResolver} instances that can resolve a {@link PartMetadata} into one or
 * more {@link Request.Body} instances.
 */
@RequiredArgsConstructor
public class PartResolverChain {
  @NonNull private final Collection<PartResolver> partResolvers;

  /**
   * Resolves the given {@link PartMetadata} into one or more {@link Request.Body} instances by
   * delegating to the first {@link PartResolver} that supports the given {@code partMetadata}.
   *
   * @param partMetadata the metadata of the part to resolve
   * @return a stream of {@link Request.Body} instances representing the resolved part
   * @throws EncodeException if an error occurs during resolving/encoding the part
   */
  public Stream<Request.Body> resolve(PartMetadata partMetadata) throws EncodeException {
    if (partMetadata.content().isEmpty()) {
      return Stream.empty();
    }

    return partResolvers.stream()
        .filter(resolver -> resolver.supports(partMetadata))
        .findFirst()
        .map(resolver -> resolver.resolve(partMetadata, this))
        .orElseThrow(() -> new EncodeException("No resolver found for part: " + partMetadata));
  }
}
