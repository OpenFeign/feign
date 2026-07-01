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

import feign.PartData;
import feign.RequestTemplate;
import java.io.File;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/** A utility class that creates a {@link RequestTemplate} from a {@link PartData} object. */
@UtilityClass
class PartDataTemplateFactory {
  /**
   * Creates a {@link RequestTemplate} from a {@link PartData} object.
   *
   * @param partData the {@link PartData} object to create the {@link RequestTemplate} from
   * @param variables the variables to use for template expansion
   * @return a {@link RequestTemplate} representing the given {@link PartData} object
   */
  RequestTemplate create(PartData partData, Map<String, ?> variables) {
    var template = new RequestTemplate();

    copyHeaders(partData, template);
    addContentTypeIfRequired(template);

    return template.resolve(variables);
  }

  private void copyHeaders(PartData from, RequestTemplate to) {
    var filename = getFilename(from.value());

    from.headers().entrySet().stream()
        .filter(entry -> entry.getKey() != null)
        .forEach(
            entry -> {
              var name = entry.getKey().trim();
              var values = entry.getValue();
              var resolvedValues =
                  filename != null && "Content-Disposition".equalsIgnoreCase(name)
                      ? appendFilenameIfMissing(values, filename)
                      : values;

              to.header(name, resolvedValues);
            });
  }

  private void addContentTypeIfRequired(RequestTemplate to) {
    var headers = to.headers();

    if (headers.containsKey("Content-Type")) {
      return;
    }

    headers.getOrDefault("Content-Disposition", List.of()).stream()
        .map(contentDisposition -> new ContentDisposition(contentDisposition).getFilename())
        .filter(Objects::nonNull)
        .findFirst()
        .ifPresent(filename -> to.header("Content-Type", getContentType(filename)));
  }

  private String getFilename(Object value) {
    if (value instanceof File) {
      return ((File) value).getName();
    }

    if (value instanceof Path) {
      var fileName = ((Path) value).getFileName();

      if (fileName != null) {
        return fileName.toString();
      }
    }

    return null;
  }

  private Collection<String> appendFilenameIfMissing(Collection<String> values, String filename) {
    return values.stream()
        .map(value -> appendFilenameIfMissing(value, filename))
        .collect(Collectors.toList());
  }

  private String appendFilenameIfMissing(String value, String filename) {
    return value != null && isMissingFilename(new ContentDisposition(value))
        ? value + "; filename=\"" + filename + '"'
        : value;
  }

  private boolean isMissingFilename(ContentDisposition contentDisposition) {
    return "form-data".equals(contentDisposition.getType())
        && contentDisposition.getFilename() == null;
  }

  private String getContentType(String filename) {
    var contentType = URLConnection.guessContentTypeFromName(filename);

    return contentType != null ? contentType : "application/octet-stream";
  }
}
