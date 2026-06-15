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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import feign.form.FormData;
import org.junit.jupiter.api.Test;

class AbstractWriterTest {

  @Test
  void fileNameWithCrlfAndQuoteIsEscaped() {
    Output output = new Output(UTF_8);
    FormData formData =
        new FormData("text/plain", "evil\"\r\nX-Injected: 1", "body".getBytes(UTF_8));

    new FormDataWriter().write(output, "boundary", "file", formData);
    String written = new String(output.toByteArray(), UTF_8);

    assertThat(written).contains("filename=\"evil%22%0D%0AX-Injected: 1\"");
    assertThat(written).doesNotContain("\r\nX-Injected: 1");
  }

  @Test
  void parameterNameWithCrlfIsEscaped() {
    Output output = new Output(UTF_8);

    new SingleParameterWriter().write(output, "boundary", "a\"\r\nX-Injected: 1", "value");
    String written = new String(output.toByteArray(), UTF_8);

    assertThat(written).contains("name=\"a%22%0D%0AX-Injected: 1\"");
    assertThat(written).doesNotContain("\r\nX-Injected: 1");
  }
}
