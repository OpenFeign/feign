/**
 * Copyright 2012-2018 The Feign Authors
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
package feign.httpclient.test;

import static org.assertj.core.api.Assertions.assertThat;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import java.io.IOException;
import feign.*;
import feign.client.AbstractClientTest;
import feign.httpclient.Http2Client;
import okhttp3.mockwebserver.MockResponse;

/**
 * Tests client-specific behavior, such as ensuring Content-Length is sent when specified.
 */
public class Http2ClientTest extends AbstractClientTest {

  public interface TestInterface {
    @RequestLine("PATCH /patch")
    @Headers({"Accept: text/plain"})
    String patch(String var1);

    @RequestLine("PATCH /patch")
    @Headers({"Accept: text/plain"})
    String patch();
  }

  @Override
  @Test
  public void testPatch() throws Exception {
    final TestInterface api =
        newBuilder().target(TestInterface.class, "https://nghttp2.org/httpbin/");
    Assertions.assertThat(api.patch(""))
        .contains("https://nghttp2.org/httpbin/patch");
  }

  @Override
  @Test
  public void noResponseBodyForPatch() {
    final TestInterface api =
        newBuilder().target(TestInterface.class, "https://nghttp2.org/httpbin/");
    Assertions.assertThat(api.patch())
        .contains("https://nghttp2.org/httpbin/patch");
  }

  @Override
  @Test
  public void reasonPhraseIsOptional() throws IOException, InterruptedException {
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
  public void testVeryLongResponseNullLength() {
    // client is too smart to fall for a body that is 8 bytes long
  }

  @Override
  public Feign.Builder newBuilder() {
    return Feign.builder().client(new Http2Client());
  }

}
