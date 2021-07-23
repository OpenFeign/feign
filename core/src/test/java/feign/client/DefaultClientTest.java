/**
 * Copyright 2012-2021 The Feign Authors
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
import static org.hamcrest.core.Is.isA;
import static org.junit.Assert.assertEquals;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.SocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import okio.Buffer;
import org.junit.Test;
import feign.Client;
import feign.Client.Proxied;
import feign.Feign;
import feign.Feign.Builder;
import feign.RetryableException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.SocketPolicy;

/**
 * Tests client-specific behavior, such as ensuring Content-Length is sent when specified.
 */
public class DefaultClientTest extends AbstractClientTest {

  protected Client disableHostnameVerification =
      new Client.Default(TrustingSSLSocketFactory.get(), new HostnameVerifier() {
        @Override
        public boolean verify(String s, SSLSession sslSession) {
          return true;
        }
      });

  @Override
  public Builder newBuilder() {
    return Feign.builder().client(new Client.Default(TrustingSSLSocketFactory.get(), null, false));
  }

  @Test
  public void retriesFailedHandshake() throws IOException, InterruptedException {
    server.useHttps(TrustingSSLSocketFactory.get("localhost"), false);
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.FAIL_HANDSHAKE));
    server.enqueue(new MockResponse());

    TestInterface api = newBuilder()
        .target(TestInterface.class, "https://localhost:" + server.getPort());

    api.post("foo");
    assertEquals(2, server.getRequestCount());
  }

  @Test
  public void canOverrideSSLSocketFactory() throws IOException, InterruptedException {
    server.useHttps(TrustingSSLSocketFactory.get("localhost"), false);
    server.enqueue(new MockResponse());

    TestInterface api = newBuilder()
        .target(TestInterface.class, "https://localhost:" + server.getPort());

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
  public void testPatch() throws Exception {
    thrown.expect(RetryableException.class);
    thrown.expectCause(isA(ProtocolException.class));
    super.testPatch();
  }

  @Test
  @Override
  public void noResponseBodyForPatch() {
    thrown.expect(RetryableException.class);
    thrown.expectCause(isA(ProtocolException.class));
    super.noResponseBodyForPatch();
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

  private final SocketAddress proxyAddress =
      new InetSocketAddress("proxy.example.com", 8080);

  /**
   * Test that the proxy is being used, but don't check the credentials. Credentials can still be
   * used, but they must be set using the appropriate system properties and testing that is not what
   * we are looking to do here.
   */
  @Test
  public void canCreateWithImplicitOrNoCredentials() throws Exception {
    Proxied proxied = new Proxied(
        TrustingSSLSocketFactory.get(), null,
        new Proxy(Type.HTTP, proxyAddress));
    assertThat(proxied).isNotNull();
    assertThat(proxied.getCredentials()).isNullOrEmpty();

    /* verify that the proxy */
    HttpURLConnection connection = proxied.getConnection(new URL("http://www.example.com"));
    assertThat(connection).isNotNull().isInstanceOf(HttpURLConnection.class);
  }

  @Test
  public void canCreateWithExplicitCredentials() throws Exception {
    Proxied proxied = new Proxied(
        TrustingSSLSocketFactory.get(), null,
        new Proxy(Type.HTTP, proxyAddress), "user", "password");
    assertThat(proxied).isNotNull();
    assertThat(proxied.getCredentials()).isNotBlank();

    HttpURLConnection connection = proxied.getConnection(new URL("http://www.example.com"));
    assertThat(connection).isNotNull().isInstanceOf(HttpURLConnection.class);
  }


  @Test
  public void canSupportGzip() throws Exception {
    /* enqueue a zipped response */
    final String responseData = "Compressed Data";
    server.enqueue(new MockResponse()
        .addHeader("Content-Encoding", "gzip")
        .setBody(new Buffer().write(compress(responseData))));

    TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    String result = api.get();

    /* verify that the response is unzipped */
    assertThat(result).isNotNull()
        .isEqualToIgnoringCase(responseData);

  }

  @Test
  public void canSupportDeflate() throws Exception {
    /* enqueue a zipped response */
    final String responseData = "Compressed Data";
    server.enqueue(new MockResponse()
        .addHeader("Content-Encoding", "deflate")
        .setBody(new Buffer().write(deflate(responseData))));

    TestInterface api = newBuilder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    String result = api.get();

    /* verify that the response is unzipped */
    assertThat(result).isNotNull()
        .isEqualToIgnoringCase(responseData);

  }

  private byte[] compress(String data) throws Exception {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length())) {
      GZIPOutputStream gzipOutputStream = new GZIPOutputStream(bos);
      gzipOutputStream.write(data.getBytes(StandardCharsets.UTF_8), 0, data.length());
      gzipOutputStream.close();
      return bos.toByteArray();
    }
  }

  private byte[] deflate(String data) throws Exception {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length())) {
      DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(bos);
      deflaterOutputStream.write(data.getBytes(StandardCharsets.UTF_8), 0, data.length());
      deflaterOutputStream.close();
      return bos.toByteArray();
    }
  }
}
