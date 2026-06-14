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
import java.io.File;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/** A {@link PartEncoder} that delegates to another {@link PartEncoder} for {@link File} content. */
@RequiredArgsConstructor
public class FilePartEncoder extends AbstractPartEncoder {
  @NonNull private final PartEncoder delegate;

  /**
   * Creates a {@link Request.Body} from the given {@link PartMetadata} by converting the content to
   * a {@link java.nio.file.Path} and delegating to the provided {@link PartEncoder}.
   *
   * @param partMetadata {@inheritDoc}
   * @return {@inheritDoc}
   */
  @Override
  public Request.Body encode(PartMetadata partMetadata) {
    return delegate.encode(toPathPartMetadata(partMetadata));
  }

  /**
   * {@inheritDoc}
   *
   * @param partMetadata {@inheritDoc}
   * @return {@code true} if the content is a {@link File} and the delegate supports it, {@code
   *     false} otherwise
   */
  @Override
  public boolean supports(PartMetadata partMetadata) {
    return partMetadata
        .content()
        .filter(
            content ->
                content instanceof File && delegate.supports(toPathPartMetadata(partMetadata)))
        .isPresent();
  }

  private PartMetadata toPathPartMetadata(PartMetadata partMetadata) {
    return new PartMetadata(
        partMetadata.name(),
        partMetadata.content().map(content -> ((File) content).toPath()).orElseThrow(),
        partMetadata.filenameOverride().orElse(null),
        partMetadata.contentTypeOverride().orElse(null));
  }
}
