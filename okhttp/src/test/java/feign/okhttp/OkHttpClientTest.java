/*
 * Copyright 2015 Netflix, Inc.
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
package feign.okhttp;

import feign.Feign.Builder;
import feign.Headers;
import feign.Param;
import feign.RequestLine;
import feign.Response;
import feign.Util;
import feign.assertj.MockWebServerAssertions;
import feign.client.AbstractClientTest;

import feign.Feign;
import okhttp3.mockwebserver.MockResponse;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/** Tests client-specific behavior, such as ensuring Content-Length is sent when specified. */
public class OkHttpClientTest extends AbstractClientTest {

  @Override
  public Builder newBuilder() {
    return Feign.builder().client(new OkHttpClient());
  }


  @Test
  public void testContentTypeWithoutCharset() throws Exception {
    server.enqueue(new MockResponse()
            .setBody("AAAAAAAA"));
    OkHttpClientTestInterface api = newBuilder()
            .target(OkHttpClientTestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.getWithContentType();
    // Response length should not be null
    assertEquals("AAAAAAAA", Util.toString(response.body().asReader()));

    MockWebServerAssertions.assertThat(server.takeRequest())
            .hasHeaders("Accept: text/plain", "Content-Type: text/plain") // Note: OkHttp adds content length.
            .hasMethod("GET");
  }


  public interface OkHttpClientTestInterface {

    @RequestLine("GET /")
    @Headers({"Accept: text/plain", "Content-Type: text/plain"})
    Response getWithContentType();
  }
}
