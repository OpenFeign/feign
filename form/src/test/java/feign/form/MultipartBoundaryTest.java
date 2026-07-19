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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import feign.RequestTemplate;
import feign.codec.Encoder;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MultipartBoundaryTest {

  private static final Encoder NOOP_DELEGATE = (object, bodyType, template) -> true;

  @Test
  void boundaryIsNotDerivedFromTheClock() {
    long before = System.currentTimeMillis();
    String boundary = generateBoundary();
    long after = System.currentTimeMillis();

    for (long millis = before; millis <= after; millis++) {
      assertThat(boundary).isNotEqualTo(Long.toHexString(millis));
    }
  }

  @Test
  void boundaryDiffersBetweenRequests() {
    assertThat(generateBoundary()).isNotEqualTo(generateBoundary());
  }

  private static String generateBoundary() {
    MultipartFormContentProcessor processor = new MultipartFormContentProcessor(NOOP_DELEGATE);
    RequestTemplate template = new RequestTemplate();

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("field", "value");
    processor.process(template, UTF_8, data);

    String contentType = template.headers().get("Content-Type").iterator().next();
    int index = contentType.indexOf("boundary=");
    return contentType.substring(index + "boundary=".length());
  }
}
