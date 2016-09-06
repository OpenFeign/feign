/*
 * Copyright 2016 Artem Labazin <xxlabaza@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ru.xxlabaza.feign.form;

import static java.util.stream.Collectors.toMap;

import feign.RequestTemplate;
import feign.codec.Encoder;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author Artem Labazin <xxlabaza@gmail.com>
 * @since 30.04.2016
 */
public class FormEncoder implements Encoder {

  private final Encoder deligate;

  private final Map<String, FormDataProcessor> processors;

  public FormEncoder() {
    this(new Encoder.Default());
  }

  public FormEncoder(Encoder delegate) {
    this.deligate = delegate;
    processors =
        Stream.of(new FormEncodedDataProcessor(), new MultipartEncodedDataProcessor())
            .collect(toMap(FormDataProcessor::getSupportetContentType, Function.identity()));
  }

  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template) {
    if (bodyType != MAP_STRING_WILDCARD && !bodyType.equals(MultipartFile.class)) {
      deligate.encode(object, bodyType, template);
      return;
    }

    String formType =
        template.headers().entrySet().stream()
            .filter(entry -> entry.getKey().equals("Content-Type"))
            .flatMap(it -> it.getValue().stream())
            .filter(it -> processors.containsKey(it))
            .findFirst()
            .orElse("");

    if (formType.isEmpty()) {
      formType = detectFormType(object, bodyType);
    }

    if (formType.isEmpty()) {
      deligate.encode(object, bodyType, template);
      return;
    }

    if (object instanceof MultipartFile) {
      MultipartFile file = (MultipartFile) object;
      Map<String, Object> data = Collections.singletonMap(file.getName(), object);
      processors.get(formType).process(data, template);
    } else {
      @SuppressWarnings("unchecked")
      Map<String, Object> data = (Map<String, Object>) object;
      processors.get(formType).process(data, template);
    }
  }

  private String detectFormType(Object object, Type bodyType) {
    if (bodyType == MultipartFile.class) {
      return MultipartEncodedDataProcessor.CONTENT_TYPE;
    }
    if (bodyType == MAP_STRING_WILDCARD) {
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) object;
      Optional<Object> multipartFile =
          map.values().stream().filter(o -> o instanceof MultipartFile).findFirst();

      if (multipartFile.isPresent()) {
        return MultipartEncodedDataProcessor.CONTENT_TYPE;
      }
    }

    return "";
  }
}
