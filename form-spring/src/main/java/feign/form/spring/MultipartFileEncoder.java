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

import feign.Request;
import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.form.MultipartFormEncoder;
import feign.form.multipart.MultipartFormBody;
import feign.form.multipart.PartBody;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

/**
 * An encoder that encodes {@link MultipartFile} objects into a multipart/form-data request body.
 */
@RequiredArgsConstructor
public class MultipartFileEncoder extends MultipartFormEncoder {
  @NonNull private final Encoder delegate;

  /**
   * Creates a new instance of {@link MultipartFileEncoder} with a default {@link
   * MultipartFormEncoder} delegate.
   */
  public MultipartFileEncoder() {
    this(new MultipartFormEncoder());
  }

  private static Stream<?> getMultipartFiles(Object object, Type bodyType) {
    if (object instanceof MultipartFile multipartFile) {
      return Stream.of(multipartFile);
    }

    if (isMultipartFileCollection(bodyType)) {
      return ((Collection<?>) object).stream();
    }

    if (object instanceof MultipartFile[] multipartFiles) {
      return Stream.of(multipartFiles);
    }

    return null;
  }

  private static boolean isMultipartFileCollection(Type bodyType) {
    return bodyType instanceof ParameterizedType parameterizedType
        && parameterizedType.getRawType() instanceof Class<?> rawClass
        && Collection.class.isAssignableFrom(rawClass)
        && isMultipartFile(parameterizedType.getActualTypeArguments());
  }

  private static boolean isMultipartFile(Type[] actualTypeArguments) {
    return actualTypeArguments.length > 0
        && actualTypeArguments[0] instanceof Class<?> genericClass
        && MultipartFile.class.isAssignableFrom(genericClass);
  }

  private static Request.Body createPartBody(Object object) {
    var multipartFile = (MultipartFile) object;

    return new PartBody(
        PartHeadersFactory.create(multipartFile), new MultipartFileBody(multipartFile));
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
    var multipartFiles = getMultipartFiles(object, bodyType);

    if (multipartFiles == null) {
      delegate.encode(object, bodyType, template);

      return;
    }

    var parts = multipartFiles.map(MultipartFileEncoder::createPartBody).toList();

    super.encode(new MultipartFormBody(parts), MultipartFormBody.class, template);
  }
}
