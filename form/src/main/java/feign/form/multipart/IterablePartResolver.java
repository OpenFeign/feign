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
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A {@link PartResolver} that resolves {@link Iterable} content by delegating to the next resolver
 * in the chain for each item in the {@link Iterable}.
 */
public class IterablePartResolver implements PartResolver {
  /**
   * Resolves the given {@code partMetadata} by treating its content as an {@link Iterable} and
   * delegating to the {@code chain} with a new {@link PartMetadata} created from the given {@code
   * partMetadata} and each item in the {@link Iterable}.
   *
   * @param partMetadata {@inheritDoc}
   * @param chain {@inheritDoc}
   * @return {@inheritDoc}
   * @throws EncodeException {@inheritDoc}
   */
  @Override
  public Stream<Request.Body> resolve(PartMetadata partMetadata, PartResolverChain chain)
      throws EncodeException {
    return StreamSupport.stream(
            ((Iterable<?>) partMetadata.content().orElseThrow()).spliterator(), false)
        .flatMap(
            item ->
                chain.resolve(
                    new PartMetadata(
                        partMetadata.name(),
                        item,
                        partMetadata.filenameOverride().orElse(null),
                        partMetadata.contentTypeOverride().orElse(null))));
  }

  /**
   * {@inheritDoc}
   *
   * @param partMetadata {@inheritDoc}
   * @return {@code true} if the content of the given {@code partMetadata} is an instance of {@link
   *     Iterable} and not an instance of {@link Path}, {@code false} otherwise.
   */
  @Override
  public boolean supports(PartMetadata partMetadata) {
    return partMetadata
        .content()
        .filter(content -> content instanceof Iterable && !(content instanceof Path))
        .isPresent();
  }
}
