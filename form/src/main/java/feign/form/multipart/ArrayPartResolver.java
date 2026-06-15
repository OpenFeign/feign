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
import java.lang.reflect.Array;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Resolves array content by creating a part for each element in the array. Byte arrays are excluded
 * since they are commonly used to represent binary data and should be treated as a single part.
 */
public class ArrayPartResolver implements PartResolver {
  /**
   * Resolves the given part metadata by delegating each array element to the {@code chain}.
   *
   * @param partMetadata {@inheritDoc}
   * @param chain {@inheritDoc}
   * @return {@inheritDoc}
   * @throws EncodeException {@inheritDoc}
   */
  @Override
  public Stream<Request.Body> resolve(PartMetadata partMetadata, PartResolverChain chain)
      throws EncodeException {
    var content = partMetadata.content().orElseThrow();

    return IntStream.range(0, Array.getLength(content))
        .mapToObj(
            i ->
                chain.resolve(
                    new PartMetadata(
                        partMetadata.name(),
                        Array.get(content, i),
                        partMetadata.filenameOverride().orElse(null),
                        partMetadata.contentTypeOverride().orElse(null))))
        .flatMap(Function.identity());
  }

  /**
   * {@inheritDoc}
   *
   * @param partMetadata {@inheritDoc}
   * @return {@code true} if the content is an array (excluding byte arrays), {@code false}
   *     otherwise
   */
  @Override
  public boolean supports(PartMetadata partMetadata) {
    return partMetadata
        .content()
        .filter(content -> content.getClass().isArray() && !(content instanceof byte[]))
        .isPresent();
  }
}
