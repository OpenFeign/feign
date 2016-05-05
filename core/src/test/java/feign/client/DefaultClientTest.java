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
package feign.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.SocketPolicy;
import okhttp3.mockwebserver.MockWebServer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ProtocolException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import feign.Client;
import feign.Feign;
import feign.FeignException;
import feign.Headers;
import feign.Logger;
import feign.RequestLine;
import feign.Response;

import static feign.Util.UTF_8;
import static feign.assertj.MockWebServerAssertions.assertThat;
import static java.util.Arrays.asList;
import static org.hamcrest.core.Is.isA;
import static org.junit.Assert.assertEquals;

/** Tests client-specific behavior, such as ensuring Content-Length is sent when specified. */
public class DefaultClientTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();
  @Rule
  public final MockWebServer server = new MockWebServer();
  Client trustSSLSockets = new Client.Default(TrustingSSLSocketFactory.get(), null);
  Client disableHostnameVerification =
      new Client.Default(TrustingSSLSocketFactory.get(), new HostnameVerifier() {
        @Override
        public boolean verify(String s, SSLSession sslSession) {
          return true;
        }
      });

  @Test
  public void parsesRequestAndResponse() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("foo").addHeader("Foo: Bar"));

    TestInterface api =
        Feign.builder().target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.post("foo");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.reason()).isEqualTo("OK");
    assertThat(response.headers())
        .containsEntry("Content-Length", asList("3"))
        .containsEntry("Foo", asList("Bar"));
    assertThat(response.body().asInputStream())
        .hasContentEqualTo(new ByteArrayInputStream("foo".getBytes(UTF_8)));

    assertThat(server.takeRequest()).hasMethod("POST")
        .hasPath("/?foo=bar&foo=baz&qux=")
        .hasHeaders("Foo: Bar", "Foo: Baz", "Qux: ", "Accept: */*", "Content-Length: 3")
        .hasBody("foo");
  }

  @Test
  public void reasonPhraseIsOptional() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setStatus("HTTP/1.1 " + 200));

    TestInterface api =
        Feign.builder().target(TestInterface.class, "http://localhost:" + server.getPort());

    Response response = api.post("foo");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.reason()).isNull();
  }

  @Test
  public void parsesErrorResponse() throws IOException, InterruptedException {
    thrown.expect(FeignException.class);
    thrown.expectMessage("status 500 reading TestInterface#get(); content:\n" + "ARGHH");

    server.enqueue(new MockResponse().setResponseCode(500).setBody("ARGHH"));

    TestInterface api =
        Feign.builder().target(TestInterface.class, "http://localhost:" + server.getPort());

    api.get();
  }

  /**
   * We currently don't include the <a href="http://java.net/jira/browse/JERSEY-639">60-line
   * workaround</a> jersey uses to overcome the lack of support for PATCH. For now, prefer okhttp.
   *
   * @see java.net.HttpURLConnection#setRequestMethod
   */
  @Test
  public void patchUnsupported() throws IOException, InterruptedException {
    thrown.expectCause(isA(ProtocolException.class));

    TestInterface
        api =
        Feign.builder().target(TestInterface.class, "http://localhost:" + server.getPort());

    api.patch();
  }

  @Test
  public void canOverrideSSLSocketFactory() throws IOException, InterruptedException {
    server.useHttps(TrustingSSLSocketFactory.get("localhost"), false);
    server.enqueue(new MockResponse());

    TestInterface api = Feign.builder()
        .client(trustSSLSockets)
        .target(TestInterface.class, "https://localhost:" + server.getPort());

    api.post("foo");
  }

  @Test
  public void canOverrideHostnameVerifier() throws IOException, InterruptedException {
    server.useHttps(TrustingSSLSocketFactory.get("bad.example.com"), false);
    server.enqueue(new MockResponse());

    TestInterface api = Feign.builder()
        .client(disableHostnameVerification)
        .target(TestInterface.class, "https://localhost:" + server.getPort());

    api.post("foo");
  }

  @Test
  public void retriesFailedHandshake() throws IOException, InterruptedException {
    server.useHttps(TrustingSSLSocketFactory.get("localhost"), false);
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.FAIL_HANDSHAKE));
    server.enqueue(new MockResponse());

    TestInterface api = Feign.builder()
        .client(trustSSLSockets)
        .target(TestInterface.class, "https://localhost:" + server.getPort());

    api.post("foo");
    assertEquals(2, server.getRequestCount());
  }

  @Test
  public void safeRebuffering() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api = Feign.builder()
        .logger(new Logger(){
          @Override
          protected void log(String configKey, String format, Object... args) {
          }
        })
        .logLevel(Logger.Level.FULL) // rebuffers the body
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    api.post("foo");
  }

  /** This shows that is a no-op or otherwise doesn't cause an NPE when there's no content. */
  @Test
  public void safeRebuffering_noContent() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setResponseCode(204));

    TestInterface api = Feign.builder()
        .logger(new Logger(){
          @Override
          protected void log(String configKey, String format, Object... args) {
          }
        })
        .logLevel(Logger.Level.FULL) // rebuffers the body
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    api.post("foo");
  }

  @Test
  public void noResponseBodyForPost() {
      server.enqueue(new MockResponse());

      TestInterface api = Feign.builder()
          .target(TestInterface.class, "http://localhost:" + server.getPort());

      api.noPostBody();
  }
  
  @Test
  public void noResponseBodyForPut() {
      server.enqueue(new MockResponse());
      
      TestInterface api = Feign.builder()
              .target(TestInterface.class, "http://localhost:" + server.getPort());
      
      api.noPutBody();
  }

  interface TestInterface {

    @RequestLine("POST /?foo=bar&foo=baz&qux=")
    @Headers({"Foo: Bar", "Foo: Baz", "Qux: ", "Content-Type: text/plain"})
    Response post(String body);

    @RequestLine("GET /")
    @Headers("Accept: text/plain")
    String get();

    @RequestLine("PATCH /")
    @Headers("Accept: text/plain")
    String patch();

    @RequestLine("POST")
    String noPostBody();
    
    @RequestLine("PUT")
    String noPutBody();
  }
}
