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
package feign.http2client.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import feign.Body;
import feign.Feign;
import feign.FeignException;
import feign.Headers;
import feign.Request;
import feign.RequestLine;
import feign.Response;
import feign.Retryer;
import feign.client.AbstractClientTest;
import feign.http2client.Http2Client;
import mockwebserver3.MockResponse;

/**
 * Tests client-specific behavior, such as ensuring Content-Length is sent when specified.
 */
@Disabled
public class Http2ClientTest extends AbstractClientTest {

  public interface TestInterface {
    @RequestLine("PATCH /patch")
    @Headers({"Accept: text/plain"})
    String patch(String var1);

    @RequestLine("PATCH /patch")
    @Headers({"Accept: text/plain"})
    String patch();

    @RequestLine("POST /timeout")
    @Headers({"Accept: text/plain"})
    String timeout();

    @RequestLine("GET /anything")
    @Body("some request body")
    String getWithBody();

    @RequestLine("DELETE /anything")
    @Body("some request body")
    String deleteWithBody();
  }

  @Override
  @Test
  public void patch() throws Exception {
    final TestInterface api =
        newBuilder().target(TestInterface.class, "https://nghttp2.org/httpbin/");
    assertThat(api.patch(""))
        .contains("https://nghttp2.org/httpbin/patch");
  }

  @Override
  @Test
  public void noResponseBodyForPatch() {
    final TestInterface api =
        newBuilder().target(TestInterface.class, "https://nghttp2.org/httpbin/");
    assertThat(api.patch())
        .contains("https://nghttp2.org/httpbin/patch");
  }

  @Override
  @Test
  public void reasonPhraseIsOptional() throws IOException, InterruptedException {
    server.enqueue(new MockResponse()
        .setStatus("HTTP/1.1 " + 200));

    final AbstractClientTest.TestInterface api = newBuilder()
        .target(AbstractClientTest.TestInterface.class, "http://localhost:" + server.getPort());

    final Response response = api.post("foo");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.reason()).isNull();
  }

  @Test
  void reasonPhraseInHeader() throws IOException, InterruptedException {
    server.enqueue(new MockResponse()
        .addHeader("Reason-Phrase", "There is A reason")
        .setStatus("HTTP/1.1 " + 200));

    final AbstractClientTest.TestInterface api = newBuilder()
        .target(AbstractClientTest.TestInterface.class, "http://localhost:" + server.getPort());

    final Response response = api.post("foo");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.reason()).isEqualTo("There is A reason");
  }

  @Override
  @Test
  public void veryLongResponseNullLength() {
    // client is too smart to fall for a body that is 8 bytes long
  }

  @Test
  void timeoutTest() {
    server.enqueue(new MockResponse().setBody("foo").setBodyDelay(30, TimeUnit.SECONDS));

    final TestInterface api = newBuilder()
        .retryer(Retryer.NEVER_RETRY)
        .options(new Request.Options(1, TimeUnit.SECONDS, 1, TimeUnit.SECONDS, true))
        .target(TestInterface.class, server.url("/").toString());

    FeignException exception = assertThrows(FeignException.class, () -> api.timeout());
    assertThat(exception).hasCauseInstanceOf(HttpTimeoutException.class);
  }

  @Test
  void getWithRequestBody() {
    final TestInterface api =
        newBuilder().target(TestInterface.class, "https://nghttp2.org/httpbin/");
    String result = api.getWithBody();
    assertThat(result)
        .contains("\"data\": \"some request body\"");
  }

  @Test
  void deleteWithRequestBody() {
    final TestInterface api =
        newBuilder().target(TestInterface.class, "https://nghttp2.org/httpbin/");
    String result = api.deleteWithBody();
    assertThat(result)
        .contains("\"data\": \"some request body\"");
  }

  @Override
  public Feign.Builder newBuilder() {
    return Feign.builder().client(new Http2Client());
  }

}
