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

import java.io.File;
import java.util.stream.Stream;

/**
 * A {@link PartContextResolver} implementation that resolves a {@link PartContext} containing a
 * {@link File} into a {@link PartContext} with the file's path as the content.
 */
public class FilePartContextResolver implements PartContextResolver {
  /**
   * {@inheritDoc}
   *
   * @param partContext {@inheritDoc}
   * @param chain {@inheritDoc}
   * @return {@inheritDoc}
   */
  @Override
  public Stream<PartContext> resolve(PartContext partContext, PartContextResolverChain chain) {
    var unwrappedPartMetadata =
        partContext
            .content()
            .filter(File.class::isInstance)
            .map(file -> partContext.toBuilder().content(((File) file).toPath()).build())
            .orElse(partContext);

    return chain.resolve(unwrappedPartMetadata);
  }
}
