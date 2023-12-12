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

import static feign.Util.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import feign.Feign;
import feign.Feign.Builder;
import feign.Response;
import feign.Util;
import feign.assertj.MockWebServerAssertions;
import feign.client.AbstractClientTest;
import mockwebserver3.MockResponse;

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
  public void patch() {}

  @Override
  public void parsesUnauthorizedResponseBody() {}

  /*
   * Google HTTP client with NetHttpTransport does not support gzip and deflate compression
   * out-of-the-box. You can replace the transport with Apache HTTP Client.
   */
  @Override
  public void canSupportGzip() throws Exception {
    assumeFalse(false, "Google HTTP client client do not support gzip compression");
  }

  @Override
  public void canSupportGzipOnError() throws Exception {
    assumeFalse(false, "Google HTTP client client do not support gzip compression");
  }

  @Override
  public void canSupportDeflate() throws Exception {
    assumeFalse(false, "Google HTTP client client do not support deflate compression");
  }

  @Override
  public void canSupportDeflateOnError() throws Exception {
    assumeFalse(false, "Google HTTP client client do not support deflate compression");
  }

  @Override
  public void canExceptCaseInsensitiveHeader() throws Exception {
    assumeFalse(false, "Google HTTP client client do not support gzip compression");
  }

  @Test
  void contentTypeHeaderGetsAddedOnce() throws Exception {
    server.enqueue(new MockResponse()
        .setBody("AAAAAAAA"));
    TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.postWithContentType("foo", "text/plain");
    // Response length should not be null
    assertThat(Util.toString(response.body().asReader(UTF_8))).isEqualTo("AAAAAAAA");

    MockWebServerAssertions.assertThat(server.takeRequest())
        .hasHeaders(entry("Content-Type", Collections.singletonList("text/plain")),
            entry("Content-Length", Collections.singletonList("3")))
        .hasMethod("POST");
  }


  @Override
  public void veryLongResponseNullLength() {
    assumeFalse(false, "JaxRS client hang if the response doesn't have a payload");
  }

}
