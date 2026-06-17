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

import feign.Request;
import org.junit.jupiter.api.Test;

class AbstractPartEncoderTest {

  @Test
  void nameAndFileNameWithCrlfAndQuoteAreEscaped() {
    PartMetadata metadata =
        new PartMetadata(
            "a\"\r\nX-Injected: 1",
            "body".getBytes(UTF_8),
            "evil\"\r\nX-Injected: 1",
            "text/plain");

    Request.Body body = new ByteArrayPartEncoder().encode(metadata);
    String disposition = ((Part) body).getHeaders().get("Content-Disposition");

    assertThat(disposition)
        .isEqualTo(
            "form-data; name=\"a%22%0D%0AX-Injected: 1\"; filename=\"evil%22%0D%0AX-Injected: 1\"");
    assertThat(disposition).doesNotContain("\r\nX-Injected: 1");
  }
}
