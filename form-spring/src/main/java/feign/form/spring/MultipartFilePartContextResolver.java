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
package feign.form.spring;

import feign.form.multipart.PartContext;
import feign.form.multipart.PartContextResolver;
import feign.form.multipart.PartContextResolverChain;
import java.util.stream.Stream;
import org.springframework.web.multipart.MultipartFile;

/**
 * A {@link PartContextResolver} implementation that resolves a {@link PartContext} containing a
 * {@link MultipartFile} into a {@link PartContext} with the unwrapped content of the {@link
 * MultipartFile}.
 */
public class MultipartFilePartContextResolver implements PartContextResolver {
  /**
   * {@inheritDoc}
   *
   * @param partContext {@inheritDoc}
   * @param chain {@inheritDoc}
   * @return {@inheritDoc}
   */
  @Override
  public Stream<PartContext> resolve(PartContext partContext, PartContextResolverChain chain) {
    var resolvedPartContext =
        partContext
            .content()
            .filter(MultipartFile.class::isInstance)
            .map(
                content -> {
                  var multipartFile = (MultipartFile) content;

                  return partContext.toBuilder()
                      .name(multipartFile.getName())
                      .filename(multipartFile.getOriginalFilename())
                      .contentType(multipartFile.getContentType())
                      .build();
                })
            .orElse(partContext);

    return chain.resolve(resolvedPartContext);
  }
}
