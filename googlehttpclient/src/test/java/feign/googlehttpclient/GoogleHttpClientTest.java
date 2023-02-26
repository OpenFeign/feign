/*
 * Copyright 2012-2023 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.googlehttpclient;

import feign.Feign;
import feign.Feign.Builder;
import feign.Response;
import feign.Util;
import feign.assertj.MockWebServerAssertions;
import feign.client.AbstractClientTest;
import okhttp3.mockwebserver.MockResponse;
import org.junit.Test;
import java.util.Collections;
import static feign.Util.UTF_8;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

public class GoogleHttpClientTest extends AbstractClientTest {
  @Override
  public Builder newBuilder() {
    return Feign.builder()
        .client(new GoogleHttpClient());
  }

  // Google http client doesn't support PATCH. See:
  // https://github.com/googleapis/google-http-java-client/issues/167
  @Override
  public void noResponseBodyForPatch() {}

  @Override
  public void testPatch() {}

  @Override
  public void parsesUnauthorizedResponseBody() {}

  /*
   * Google HTTP client with NetHttpTransport does not support gzip and deflate compression
   * out-of-the-box. You can replace the transport with Apache HTTP Client.
   */
  @Override
  public void canSupportGzip() throws Exception {
    assumeFalse("Google HTTP client client do not support gzip compression", false);
  }

  @Override
  public void canSupportDeflate() throws Exception {
    assumeFalse("Google HTTP client client do not support deflate compression", false);
  }

  @Override
  public void canExceptCaseInsensitiveHeader() throws Exception {
    assumeFalse("Google HTTP client client do not support gzip compression", false);
  }

  @Test
  public void testContentTypeHeaderGetsAddedOnce() throws Exception {
    server.enqueue(new MockResponse()
        .setBody("AAAAAAAA"));
    TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.postWithContentType("foo", "text/plain");
    // Response length should not be null
    assertEquals("AAAAAAAA", Util.toString(response.body().asReader(UTF_8)));

    MockWebServerAssertions.assertThat(server.takeRequest())
        .hasHeaders(entry("Content-Type", Collections.singletonList("text/plain")),
            entry("Content-Length", Collections.singletonList("3")))
        .hasMethod("POST");
  }
}
