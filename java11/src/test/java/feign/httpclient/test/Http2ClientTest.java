/**
 * Copyright 2012-2018 The Feign Authors
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.httpclient.test;

import feign.*;
import feign.httpclient.Http2Client;
import org.assertj.core.api.Assertions;
import org.junit.Test;

/** Tests client-specific behavior, such as ensuring Content-Length is sent when specified. */
public class Http2ClientTest {

  public interface TestInterface {
    @RequestLine("POST /?foo=bar&foo=baz&qux=")
    @Headers({"Foo: Bar", "Foo: Baz", "Qux: ", "Content-Type: text/plain"})
    Response post(String var1);

    @RequestLine("POST /path/{to}/resource")
    @Headers({"Accept: text/plain"})
    Response post(@Param("to") String var1, String var2);

    @RequestLine("GET /")
    @Headers({"Accept: text/plain"})
    String get();

    @RequestLine("PATCH /patch")
    @Headers({"Accept: text/plain"})
    String patch(String var1);

    @RequestLine("POST")
    String noPostBody();

    @RequestLine("PUT")
    String noPutBody();

    @RequestLine("POST /?foo=bar&foo=baz&qux=")
    @Headers({"Foo: Bar", "Foo: Baz", "Qux: ", "Content-Type: {contentType}"})
    Response postWithContentType(String var1, @Param("contentType") String var2);
  }

  @Test
  public void testPatch() throws Exception {
    TestInterface api = newBuilder().target(TestInterface.class, "https://nghttp2.org/httpbin/");
    Assertions.assertThat(api.patch("")).contains("https://nghttp2.org/httpbin/patch");
  }

  public Feign.Builder newBuilder() {
    return Feign.builder().client(new Http2Client());
  }
}
