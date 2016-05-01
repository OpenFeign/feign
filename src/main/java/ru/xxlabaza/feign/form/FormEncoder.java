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

import feign.RequestTemplate;
import feign.codec.Encoder;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

  public FormEncoder(Encoder deligate) {
    this.deligate = deligate;
    processors =
        Stream.of(new FormEncodedDataProcessor(), new MultipartEncodedDataProcessor())
            .collect(
                Collectors.toMap(FormDataProcessor::getSupportetContentType, Function.identity()));
  }

  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template) {
    if (bodyType != MAP_STRING_WILDCARD) {
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
      deligate.encode(object, bodyType, template);
      return;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) object;
    processors.get(formType).process(data, template);
  }
}
