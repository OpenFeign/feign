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
package feign.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.entry;

import feign.Client;
import feign.Feign;
import feign.Feign.Builder;
import feign.Request;
import feign.Request.HttpMethod;
import feign.Request.Options;
import feign.Response;
import feign.RetryableException;
import feign.Util;
import feign.assertj.MockWebServerAssertions;
import feign.client.AbstractClientTest;
import feign.client.TrustingSSLSocketFactory;
import feign.core.DefaultClient.Proxied;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Tests client-specific behavior, such as ensuring Content-Length is sent when specified. */
public class DefaultClientTest extends AbstractClientTest {

  protected Client disableHostnameVerification =
      new DefaultClient(TrustingSSLSocketFactory.get(), (_, _) -> true);

  @Override
  public Builder newBuilder() {
    return Feign.builder().client(new DefaultClient(TrustingSSLSocketFactory.get(), null, false));
  }

  @Test
  void retriesFailedHandshake() throws Exception {
    server.useHttps(TrustingSSLSocketFactory.get("localhost"));
    server.enqueue(new MockResponse.Builder().failHandshake().build());
    server.enqueue(new MockResponse.Builder().build());

    TestInterface api =
        newBuilder().target(TestInterface.class, "https://localhost:" + server.getPort());

    api.post("foo");
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  void gzipDecodedBodyReportsUnknownLength() throws Exception {
    // Accept-Encoding is set explicitly so HttpURLConnection leaves the gzip body for the client
    // to decode, exercising DefaultClient's own decompression path.
    server.enqueue(
        new MockResponse.Builder()
            .addHeader("Content-Encoding", "gzip")
            .body(new Buffer().write(gzip("Compressed Data")))
            .build());

    Map<String, Collection<String>> headers = new LinkedHashMap<>();
    headers.put("Accept-Encoding", Collections.singletonList("gzip"));
    Request request =
        Request.create(HttpMethod.GET, "http://localhost:" + server.getPort(), headers, null, null);

    Response response = new DefaultClient(null, null, false).execute(request, new Options());

    // the body is decompressed, so the compressed Content-Length must not be reported as the length
    assertThat(response.body().length()).isNull();
    assertThat(Util.toString(response.body().asReader(StandardCharsets.UTF_8)))
        .isEqualTo("Compressed Data");
  }

  private static byte[] gzip(String data) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
      gzip.write(data.getBytes(StandardCharsets.UTF_8));
    }
    return bos.toByteArray();
  }

  @Test
  void canOverrideSSLSocketFactory() throws Exception {
    server.useHttps(TrustingSSLSocketFactory.get("localhost"));
    server.enqueue(new MockResponse.Builder().build());

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
    RetryableException exception =
        assertThatExceptionOfType(RetryableException.class).isThrownBy(super::patch).actual();
    assertThat(exception).hasCauseInstanceOf(ProtocolException.class);
  }

  @Test
  @Override
  public void noResponseBodyForPost() throws Exception {
    super.noResponseBodyForPost();
    MockWebServerAssertions.assertThat(server.takeRequest())
        .hasMethod("POST")
        .hasNoHeaderNamed("Content-Type");
  }

  @Test
  @EnabledIfSystemProperty(named = "sun.net.http.allowRestrictedHeaders", matches = "true")
  void noRequestBodyForPostWithAllowRestrictedHeaders() throws Exception {
    super.noResponseBodyForPost();
    MockWebServerAssertions.assertThat(server.takeRequest())
        .hasMethod("POST")
        .hasNoHeaderNamed("Content-Type")
        .hasHeaders(entry("Content-Length", Collections.singletonList("0")));
  }

  @Test
  void lowerCaseContentLengthHeaderIsUsedForFixedLengthStreamingMode() throws Exception {
    server.enqueue(new MockResponse.Builder().build());
    byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
    Map<String, Collection<String>> headers = new LinkedHashMap<>();
    headers.put("content-length", Collections.singletonList(String.valueOf(body.length)));
    Request request =
        Request.create(
            HttpMethod.POST,
            "http://localhost:" + server.getPort() + "/",
            headers,
            Request.Body.of(body),
            null);

    // the two-arg constructor disables request buffering, so a recognised Content-Length selects
    // fixed-length streaming mode and the JDK emits the header itself, exactly once
    new DefaultClient(null, null).execute(request, new Request.Options());

    RecordedRequest recordedRequest = server.takeRequest();
    assertThat(recordedRequest.getHeaders().values("Content-Length"))
        .containsExactly(String.valueOf(body.length));
    assertThat(recordedRequest.getHeaders().get("Transfer-Encoding")).isNull();
  }

  @Test
  @EnabledIfSystemProperty(named = "sun.net.http.allowRestrictedHeaders", matches = "true")
  public void contentLengthHeaderIsNotDuplicatedForBodylessRequest() throws Exception {
    server.enqueue(new MockResponse.Builder().build());
    Map<String, Collection<String>> headers = new LinkedHashMap<>();
    headers.put("content-length", Collections.singletonList("0"));
    Request request =
        Request.create(
            HttpMethod.POST, "http://localhost:" + server.getPort() + "/", headers, null, null);

    new DefaultClient(null, null).execute(request, new Request.Options());

    assertThat(server.takeRequest().getHeaders().values("Content-Length")).containsExactly("0");
  }

  @Test
  void emptyBodyDoesNotConvertGetToPost() throws Exception {
    server.enqueue(new MockResponse.Builder().body("foo").build());
    Request request =
        Request.create(
            HttpMethod.GET,
            "http://localhost:" + server.getPort() + "/",
            Collections.emptyMap(),
            Request.Body.of(new byte[0]),
            null);

    new DefaultClient(null, null).execute(request, new Request.Options());

    MockWebServerAssertions.assertThat(server.takeRequest()).hasMethod("GET");
  }

  @Test
  @Override
  public void emptyStringBodyForPost() throws Exception {
    super.emptyStringBodyForPost();
    MockWebServerAssertions.assertThat(server.takeRequest())
        .hasMethod("POST")
        .hasNoHeaderNamed("Content-Type");
  }

  @Test
  @Override
  public void noResponseBodyForPut() throws Exception {
    super.noResponseBodyForPut();
    MockWebServerAssertions.assertThat(server.takeRequest())
        .hasMethod("PUT")
        .hasNoHeaderNamed("Content-Type");
  }

  @Test
  @EnabledIfSystemProperty(named = "sun.net.http.allowRestrictedHeaders", matches = "true")
  void noResponseBodyForPutWithAllowRestrictedHeaders() throws Exception {
    super.noResponseBodyForPut();
    MockWebServerAssertions.assertThat(server.takeRequest())
        .hasMethod("PUT")
        .hasNoHeaderNamed("Content-Type")
        .hasHeaders(entry("Content-Length", Collections.singletonList("0")));
  }

  @Test
  @Override
  public void noResponseBodyForPatch() {
    RetryableException exception =
        assertThatExceptionOfType(RetryableException.class)
            .isThrownBy(super::noResponseBodyForPatch)
            .actual();
    assertThat(exception).hasCauseInstanceOf(ProtocolException.class);
  }

  /**
   * {@link java.net.HttpURLConnection} does not support the QUERY method. For now, prefer okhttp.
   *
   * @see java.net.HttpURLConnection#setRequestMethod
   */
  @Test
  @Override
  public void query() throws Exception {
    RetryableException exception =
        assertThatExceptionOfType(RetryableException.class).isThrownBy(super::query).actual();
    assertThat(exception).hasCauseInstanceOf(ProtocolException.class);
  }

  @Test
  void canOverrideHostnameVerifier() throws Exception {
    server.useHttps(TrustingSSLSocketFactory.get("bad.example.com"));
    server.enqueue(new MockResponse.Builder().build());

    TestInterface api =
        Feign.builder()
            .client(disableHostnameVerification)
            .target(TestInterface.class, "https://localhost:" + server.getPort());

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
        new Proxied(TrustingSSLSocketFactory.get(), null, new Proxy(Proxy.Type.HTTP, proxyAddress));
    assertThat(proxied).isNotNull();
    assertThat(proxied.getCredentials()).isNullOrEmpty();

    /* verify that the proxy */
    HttpURLConnection connection =
        proxied.getConnection(URI.create("http://www.example.com").toURL());
    assertThat(connection).isInstanceOf(HttpURLConnection.class);
  }

  @Test
  void canCreateWithExplicitCredentials() throws Exception {
    Proxied proxied =
        new Proxied(
            TrustingSSLSocketFactory.get(),
            null,
            new Proxy(Proxy.Type.HTTP, proxyAddress),
            "user",
            "password");
    assertThat(proxied).isNotNull();
    assertThat(proxied.getCredentials()).isNotBlank();

    HttpURLConnection connection =
        proxied.getConnection(URI.create("http://www.example.com").toURL());
    assertThat(connection).isInstanceOf(HttpURLConnection.class);
  }
}
