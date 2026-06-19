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
package feign.form;

import feign.RequestTemplate;
import feign.Util;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.core.codec.DefaultEncoder;
import feign.form.multipart.MultipartFormBodyFactory;
import feign.form.util.PojoUtil;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/** An {@link Encoder} that encodes a request body as {@code multipart/form-data}. */
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MultipartFormEncoder implements Encoder {
  @NonNull @Builder.Default private final Encoder delegate = new DefaultEncoder();

  @NonNull @Builder.Default
  private final MultipartFormBodyFactory multipartFormBodyFactory = new MultipartFormBodyFactory();

  private static boolean isMultipart(RequestTemplate template) {
    return template.headers().getOrDefault(Util.CONTENT_TYPE, List.of()).stream()
        .anyMatch(MultipartFormEncoder::isMultipart);
  }

  private static boolean isMultipart(String contentType) {
    return contentType.trim().toLowerCase().startsWith("multipart/form-data");
  }

  private static Map<String, Object> getFormData(Object object, Type bodyType) {
    if (object instanceof Map) {
      @SuppressWarnings("unchecked")
      var formData = (Map<String, Object>) object;

      return formData;
    }

    return PojoUtil.isUserPojo(bodyType) ? PojoUtil.toMap(object) : Collections.emptyMap();
  }

  /**
   * Encodes the given {@code object} as a multipart form body if the request template indicates a
   * {@code multipart/form-data} content type. Otherwise, delegates to the provided {@link Encoder}.
   *
   * @param object {@inheritDoc}
   * @param bodyType {@inheritDoc}
   * @param template {@inheritDoc}
   * @throws EncodeException {@inheritDoc}
   */
  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template)
      throws EncodeException {
    if (!isMultipart(template)) {
      delegate.encode(object, bodyType, template);
      return;
    }

    var formData = getFormData(object, bodyType);

    if (formData.isEmpty()) {
      delegate.encode(object, bodyType, template);
      return;
    }

    var multipartFormBody = multipartFormBodyFactory.create(formData);

    template.removeHeader(Util.CONTENT_TYPE);
    template.headerLiteral(
        Util.CONTENT_TYPE, "multipart/form-data; boundary=" + multipartFormBody.boundary());
    template.body(multipartFormBody);
  }
}
