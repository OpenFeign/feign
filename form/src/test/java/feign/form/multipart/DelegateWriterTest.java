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

import static org.assertj.core.api.Assertions.assertThat;

import feign.Request;
import feign.codec.Encoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DelegateWriterTest {

  private static final String BOUNDARY = "boundary";

  private static final String KEY = "metadata";

  @Test
  void usesContentTypeFromDelegate() throws Exception {
    Encoder delegate =
        (object, bodyType, template) -> {
          template.header("Content-Type", "application/json");
          template.body(Request.Body.of("{\"hash\":\"somehash\"}"));
          return true;
        };

    assertThat(write(delegate))
        .contains("Content-Type: application/json")
        .doesNotContain("Content-Type: text/plain");
  }

  @Test
  void fallsBackToTextPlainWhenDelegateSetsNoContentType() throws Exception {
    Encoder delegate =
        (object, bodyType, template) -> {
          template.body(Request.Body.of("plain"));
          return true;
        };

    assertThat(write(delegate)).contains("Content-Type: text/plain; charset=UTF-8");
  }

  private static String write(Encoder delegate) throws Exception {
    DelegateWriter writer = new DelegateWriter(delegate);
    try (Output output = new Output(StandardCharsets.UTF_8)) {
      writer.write(output, BOUNDARY, KEY, new Object());
      return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
  }
}
