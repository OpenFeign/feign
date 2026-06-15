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
import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import java.util.Collection;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * A {@link PartEncoder} that delegates the encoding of the part body to a collection of {@link
 * Encoder}s based on the content type.
 */
@RequiredArgsConstructor
public class DelegatingPartEncoder extends AbstractPartEncoder {
  @NonNull private final Collection<Encoder> delegates;

  /**
   * Creates a {@link Request.Body} for the given {@link PartMetadata} by delegating the encoding of
   * the part body to the appropriate {@link Encoder} based on the content type.
   *
   * @param partMetadata {@inheritDoc}
   * @return {@inheritDoc}
   * @throws EncodeException {@inheritDoc}
   */
  @Override
  public Request.Body encode(PartMetadata partMetadata) throws EncodeException {
    var contentType = partMetadata.contentTypeOverride().orElseThrow();

    return new Part(
        createHeaders(
            partMetadata.name(), partMetadata.filenameOverride().orElse(null), contentType),
        createBody(contentType, partMetadata.content().orElse(null)));
  }

  /**
   * {@inheritDoc}
   *
   * @param partMetadata {@inheritDoc}
   * @return {@code true} if there is an {@link Encoder} available for the content type, {@code
   *     false} otherwise
   */
  @Override
  public boolean supports(PartMetadata partMetadata) {
    return partMetadata.contentTypeOverride().flatMap(this::findEncoder).isPresent();
  }

  private Optional<Encoder> findEncoder(String contentType) {
    return delegates.stream().filter(encoder -> encoder.supports(contentType)).findFirst();
  }

  private Request.Body createBody(String contentType, Object content) throws EncodeException {
    var encoder =
        findEncoder(contentType)
            .orElseThrow(
                () -> new EncodeException("No encoder found for content type: " + contentType));
    var requestTemplate = new RequestTemplate();

    encoder.encode(content, content.getClass(), requestTemplate);

    return requestTemplate
        .requestBody()
        .orElseThrow(() -> new EncodeException("Body part was not encoded: " + content));
  }
}
