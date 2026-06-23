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
package feign.http2client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Http2ClientHeadersTest {

  @Test
  void filtersAllRestrictedHeadersByDefault() {
    assertThat(Http2Client.disallowedHeaders(null))
        .contains("connection", "content-length", "expect", "host", "upgrade");
  }

  @Test
  void allowsHeaderListedInSystemProperty() {
    assertThat(Http2Client.disallowedHeaders("host"))
        .doesNotContain("host")
        .contains("connection", "content-length", "expect", "upgrade");
  }

  @Test
  void allowsMultipleHeadersCaseInsensitivelyAndIgnoresWhitespace() {
    assertThat(Http2Client.disallowedHeaders("Host, Connection"))
        .doesNotContain("host", "connection")
        .contains("content-length", "expect", "upgrade");
  }
}
