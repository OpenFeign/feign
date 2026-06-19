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

import java.net.URLConnection;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * A {@link PartContextResolver} implementation that resolves a {@link PartContext} containing a
 * {@link Path} into a {@link PartContext} with the file's name and content type as metadata.
 */
public class PathPartContextResolver implements PartContextResolver {
  private static String resolveFilename(PartContext partContext, Path path) {
    return partContext
        .filename()
        .orElseGet(
            () -> {
              var filename = path.getFileName();

              return filename != null ? filename.toString() : null;
            });
  }

  private static String resolveContentType(PartContext partContext, String filename) {
    return partContext.contentType().orElseGet(() -> guessContentTypeFromName(filename));
  }

  private static String guessContentTypeFromName(String filename) {
    if (filename != null) {
      var guessedContentType = URLConnection.guessContentTypeFromName(filename);

      if (guessedContentType != null) {
        return guessedContentType;
      }
    }

    return "application/octet-stream";
  }

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
            .filter(Path.class::isInstance)
            .map(
                content -> {
                  var path = (Path) content;
                  var filename = resolveFilename(partContext, path);
                  var contentType = resolveContentType(partContext, filename);

                  return partContext.toBuilder()
                      .filename(filename)
                      .contentType(contentType)
                      .build();
                })
            .orElse(partContext);

    return chain.resolve(unwrappedPartMetadata);
  }
}
