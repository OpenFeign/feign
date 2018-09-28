/**
 * Copyright 2012-2019 The Feign Authors
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

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.SocketAddress;
import java.net.URL;

import org.junit.Test;

import feign.Client.Proxied;

public class ProxiedClientTest extends DefaultClientTest {

  private final SocketAddress proxyAddress =
      new InetSocketAddress("proxy.example.com", 8080);

  /**
   * Test that the proxy is being used, but don't check the credentials.  Credentials can still
   * be used, but they must be set using the appropriate system properties and testing that is
   * not what we are looking to do here.
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
}
