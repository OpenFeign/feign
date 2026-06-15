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
import java.net.URLConnection;
import java.nio.file.Path;

/** Encodes a {@link Path} as a multipart part. */
public class PathPartEncoder extends AbstractPartEncoder {
  /**
   * Encodes the given {@link PartMetadata} into a {@link Request.Body}.
   *
   * @param partMetadata {@inheritDoc}
   * @return {@inheritDoc}
   */
  @Override
  public Request.Body encode(PartMetadata partMetadata) {
    var path = (Path) partMetadata.content().orElseThrow();
    var filename =
        partMetadata
            .filenameOverride()
            .orElseGet(
                () -> {
                  var fileName = path.getFileName();

                  return fileName != null ? fileName.toString() : null;
                });
    var contentType =
        partMetadata
            .contentTypeOverride()
            .orElseGet(
                () -> {
                  if (filename != null) {
                    var guessedContentType = URLConnection.guessContentTypeFromName(filename);

                    if (guessedContentType != null) {
                      return guessedContentType;
                    }
                  }

                  return "application/octet-stream";
                });

    return new Part(
        createHeaders(partMetadata.name(), filename, contentType), new Request.PathBody(path));
  }

  /**
   * {@inheritDoc}
   *
   * @param partMetadata {@inheritDoc}
   * @return {@code true} if the content of the given {@code partMetadata} is an instance of {@link
   *     Path}, {@code false} otherwise.
   */
  @Override
  public boolean supports(PartMetadata partMetadata) {
    return partMetadata.content().filter(Path.class::isInstance).isPresent();
  }
}
