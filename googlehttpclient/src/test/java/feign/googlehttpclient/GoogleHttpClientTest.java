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
package feign.googlehttpclient;

import static feign.Util.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import feign.Feign;
import feign.Feign.Builder;
import feign.Headers;
import feign.Request;
import feign.RequestLine;
import feign.Response;
import feign.Util;
import feign.assertj.MockWebServerAssertions;
import feign.client.AbstractClientTest;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class GoogleHttpClientTest extends AbstractClientTest {
  @Override
  public Builder newBuilder() {
    return Feign.builder().client(new GoogleHttpClient());
  }

  @Test
  @Disabled("NetHttpTransport does not support PATCH")
  @Override
  public void patch() {}

  @Test
  @Disabled("NetHttpTransport does not support PATCH")
  @Override
  public void noResponseBodyForPatch() {}

  @Test
  @Disabled("NetHttpTransport does not support QUERY")
  @Override
  public void query() {}

  @Test
  @Disabled("NetHttpTransport returns an empty body for 401 responses")
  @Override
  public void parsesUnauthorizedResponseBody() {}

  @Test
  @Disabled("NetHttpTransport decompresses gzip but not deflate")
  @Override
  public void canSupportDeflate() {}

  @Test
  @Disabled("NetHttpTransport decompresses gzip but not deflate")
  @Override
  public void canSupportDeflateOnError() {}

  @Test
  void contentTypeHeaderGetsAddedOnce() throws Exception {
    server.enqueue(new MockResponse().setBody("AAAAAAAA"));
    TestInterface api =
        newBuilder().target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.postWithContentType("foo", "text/plain");
    // Response length should not be null
    assertThat(Util.toString(response.body().asReader(UTF_8))).isEqualTo("AAAAAAAA");

    MockWebServerAssertions.assertThat(server.takeRequest())
        .hasHeaders(
            entry("Content-Type", Collections.singletonList("text/plain")),
            entry("Content-Length", Collections.singletonList("3")))
        .hasMethod("POST");
  }

  @Test
  void lowerCaseContentTypeHeaderIsSentOnce() throws Exception {
    server.enqueue(new MockResponse());
    LowerCaseContentTypeApi api =
        newBuilder().target(LowerCaseContentTypeApi.class, "http://localhost:" + server.getPort());

    api.post("foo");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeaders().values("Content-Type")).containsExactly("text/plain");
  }

  @Test
  void lowerCaseContentTypeIsUsedForTheBodyWithCaseSensitiveHeaders() throws Exception {
    server.enqueue(new MockResponse());
    Map<String, Collection<String>> headers = new HashMap<>();
    headers.put("content-type", Collections.singletonList("application/json"));
    Request request =
        Request.create(
            Request.HttpMethod.POST,
            "http://localhost:" + server.getPort(),
            headers,
            "{}".getBytes(UTF_8),
            UTF_8,
            null);

    new GoogleHttpClient().execute(request, new Request.Options());

    assertThat(server.takeRequest().getHeaders().values("Content-Type"))
        .containsExactly("application/json");
  }

  interface LowerCaseContentTypeApi {
    @RequestLine("POST /")
    @Headers("content-type: text/plain")
    void post(String body);
  }
}
