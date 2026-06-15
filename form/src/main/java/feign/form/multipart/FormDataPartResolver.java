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
import java.util.stream.Stream;

/**
 * A {@link PartResolver} that resolves {@link FormData} into one or more {@link Request.Body}
 * instances.
 */
public class FormDataPartResolver implements PartResolver {
  /**
   * Resolves the given {@code partMetadata} into one or more {@link Request.Body} instances by
   * delegating to the {@code chain} with a new {@link PartMetadata} created from the given {@code
   * partMetadata} and the content of the {@link FormData}.
   *
   * @param partMetadata {@inheritDoc}
   * @param chain {@inheritDoc}
   * @return {@inheritDoc}
   * @throws EncodeException {@inheritDoc}
   */
  @Override
  public Stream<Request.Body> resolve(PartMetadata partMetadata, PartResolverChain chain)
      throws EncodeException {
    var formData = (FormData<?>) partMetadata.content().orElseThrow();

    return chain.resolve(
        new PartMetadata(
            partMetadata.name(),
            formData.content().orElse(null),
            formData.filename().orElse(null),
            formData.contentType().orElse(null)));
  }

  /**
   * {@inheritDoc}
   *
   * @param partMetadata {@inheritDoc}
   * @return {@code true} if the content of the given {@code partMetadata} is an instance of {@link
   *     FormData}, {@code false} otherwise.
   */
  @Override
  public boolean supports(PartMetadata partMetadata) {
    return partMetadata.content().filter(FormData.class::isInstance).isPresent();
  }
}
