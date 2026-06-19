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

import java.lang.reflect.Array;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Resolves a {@link PartContext} that contains an array into multiple {@link PartContext}
 * instances, one for each element in the array.
 */
public class ArrayPartContextResolver implements PartContextResolver {
  /**
   * {@inheritDoc}
   *
   * @param partContext {@inheritDoc}
   * @param chain {@inheritDoc}
   * @return {@inheritDoc}
   */
  @Override
  public Stream<PartContext> resolve(PartContext partContext, PartContextResolverChain chain) {
    return partContext
        .content()
        .filter(content -> content.getClass().isArray() && !(content instanceof byte[]))
        .map(
            array ->
                IntStream.range(0, Array.getLength(array))
                    .mapToObj(
                        i -> {
                          var flattenedPartMetadata =
                              partContext.toBuilder().content(Array.get(array, i)).build();

                          return chain.resolve(flattenedPartMetadata);
                        })
                    .flatMap(Function.identity()))
        .orElseGet(() -> chain.resolve(partContext));
  }
}
