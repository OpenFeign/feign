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

import feign.MultipartFormData;
import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.core.codec.DefaultEncoder;
import feign.form.multipart.MultipartFormBody;
import feign.form.multipart.PartBodyFactory;
import java.lang.reflect.Type;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * An encoder that encodes {@link MultipartFormData} and {@link MultipartFormBody} objects into a
 * multipart/form-data request body.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MultipartFormEncoder implements Encoder {
  @Builder.Default @NonNull private final Encoder delegate = defaultEncoder();

  @Builder.Default @NonNull
  private final PartBodyFactory partBodyFactory = new PartBodyFactory(List.of(defaultEncoder()));

  private static Encoder defaultEncoder() {
    return new DefaultEncoder();
  }

  /**
   * {@inheritDoc}
   *
   * @param object {@inheritDoc}
   * @param bodyType {@inheritDoc}
   * @param template {@inheritDoc}
   * @throws EncodeException {@inheritDoc}
   */
  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template)
      throws EncodeException {
    if (object instanceof MultipartFormBody) {
      var formBody = (MultipartFormBody) object;

      template.removeHeader("Content-Type");
      template.headerLiteral(
          "Content-Type", "multipart/form-data; boundary=" + formBody.boundary());
      template.body(formBody);

      return;
    }

    if (object instanceof MultipartFormData) {
      var formData = (MultipartFormData) object;
      var variables = formData.variables();
      var formBody =
          formData.parts().stream()
              .flatMap(part -> partBodyFactory.create(part, variables))
              .collect(Collectors.collectingAndThen(Collectors.toList(), MultipartFormBody::new));

      encode(formBody, MultipartFormBody.class, template);

      return;
    }

    delegate.encode(object, bodyType, template);
  }
}
