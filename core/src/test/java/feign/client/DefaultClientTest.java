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
package feign.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.SocketAddress;
import java.net.URL;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import feign.Client;
import feign.Client.Proxied;
import feign.Feign;
import feign.Feign.Builder;
import feign.RetryableException;
import feign.assertj.MockWebServerAssertions;
import mockwebserver3.MockResponse;
import mockwebserver3.SocketPolicy;

/**
 * Tests client-specific behavior, such as ensuring Content-Length is sent when specified.
 */
public class DefaultClientTest extends AbstractClientTest {

  protected Client disableHostnameVerification = new Client.Default(TrustingSSLSocketFactory.get(),
      (s, sslSession) -> true);

  @Override
  public Builder newBuilder() {
    return Feign.builder().client(new Client.Default(TrustingSSLSocketFactory.get(), null, false));
  }

  @Test
  void retriesFailedHandshake() throws IOException, InterruptedException {
    server.useHttps(TrustingSSLSocketFactory.get("localhost"));
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.FAIL_HANDSHAKE));
    server.enqueue(new MockResponse());

    TestInterface api =
        newBuilder().target(TestInterface.class, "https://localhost:" + server.getPort());

    api.post("foo");
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  void canOverrideSSLSocketFactory() throws IOException, InterruptedException {
    server.useHttps(TrustingSSLSocketFactory.get("localhost"));
    server.enqueue(new MockResponse());

    TestInterface api =
        newBuilder().target(TestInterface.class, "https://localhost:" + server.getPort());

    api.post("foo");
  }

  /**
   * We currently don't include the <a href="http://java.net/jira/browse/JERSEY-639">60-line
   * workaround</a> jersey uses to overcome the lack of support for PATCH. For now, prefer okhttp.
   *
   * @see java.net.HttpURLConnection#setRequestMethod
   */
  @Test
  @Override
  public void patch() throws Exception {
    RetryableException exception = assertThrows(RetryableException.class, super::patch);
    assertThat(exception).hasCauseInstanceOf(ProtocolException.class);
  }

  @Override
  public void noResponseBodyForPost() throws Exception {
    super.noResponseBodyForPost();
    MockWebServerAssertions.assertThat(server.takeRequest()).hasMethod("POST")
        .hasHeaders(entry("Content-Length", Collections.singletonList("0")));
  }

  @Override
  public void noResponseBodyForPut() throws Exception {
    super.noResponseBodyForPut();
    MockWebServerAssertions.assertThat(server.takeRequest()).hasMethod("PUT")
        .hasHeaders(entry("Content-Length", Collections.singletonList("0")));
  }

  @Test
  @Override
  public void noResponseBodyForPatch() {
    RetryableException exception =
        assertThrows(RetryableException.class, super::noResponseBodyForPatch);
    assertThat(exception).hasCauseInstanceOf(ProtocolException.class);
  }

  @Test
  void canOverrideHostnameVerifier() throws IOException, InterruptedException {
    server.useHttps(TrustingSSLSocketFactory.get("bad.example.com"));
    server.enqueue(new MockResponse());

    TestInterface api =
        Feign.builder().client(disableHostnameVerification).target(TestInterface.class,
            "https://localhost:" + server.getPort());

    api.post("foo");
  }

  private final SocketAddress proxyAddress = new InetSocketAddress("proxy.example.com", 8080);

  /**
   * Test that the proxy is being used, but don't check the credentials. Credentials can still be
   * used, but they must be set using the appropriate system properties and testing that is not what
   * we are looking to do here.
   */
  @Test
  void canCreateWithImplicitOrNoCredentials() throws Exception {
    Proxied proxied =
        new Proxied(TrustingSSLSocketFactory.get(), null, new Proxy(Type.HTTP, proxyAddress));
    assertThat(proxied).isNotNull();
    assertThat(proxied.getCredentials()).isNullOrEmpty();

    /* verify that the proxy */
    HttpURLConnection connection = proxied.getConnection(new URL("http://www.example.com"));
    assertThat(connection).isNotNull().isInstanceOf(HttpURLConnection.class);
  }

  @Test
  void canCreateWithExplicitCredentials() throws Exception {
    Proxied proxied = new Proxied(TrustingSSLSocketFactory.get(), null,
        new Proxy(Type.HTTP, proxyAddress), "user",
        "password");
    assertThat(proxied).isNotNull();
    assertThat(proxied.getCredentials()).isNotBlank();

    HttpURLConnection connection = proxied.getConnection(new URL("http://www.example.com"));
    assertThat(connection).isNotNull().isInstanceOf(HttpURLConnection.class);
  }

}
