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
package feign;

import static feign.Util.checkArgument;
import static feign.Util.checkNotNull;
import static feign.Util.isNotBlank;

import feign.Request.Options;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;

/** Submits HTTP {@link Request requests}. Implementations are expected to be thread-safe. */
public interface Client {

  /**
   * Executes a request against its {@link Request#url() url} and returns a response.
   *
   * @param request safe to replay.
   * @param options options to apply to this request.
   * @return connected response, {@link Response.Body} is absent or unread.
   * @throws IOException on a network error connecting to {@link Request#url()}.
   */
  Response execute(Request request, Options options) throws IOException;

  /**
   * @deprecated use {@link DefaultClient} instead.
   */
  @Deprecated
  class Default extends DefaultClient {

    public Default(SSLSocketFactory sslContextFactory, HostnameVerifier hostnameVerifier) {
      super(sslContextFactory, hostnameVerifier);
    }

    public Default(
        SSLSocketFactory sslContextFactory,
        HostnameVerifier hostnameVerifier,
        boolean disableRequestBuffering) {
      super(sslContextFactory, hostnameVerifier, disableRequestBuffering);
    }
  }

  /** Client that supports a {@link java.net.Proxy}. */
  class Proxied extends Default {

    public static final String PROXY_AUTHORIZATION = "Proxy-Authorization";
    private final Proxy proxy;
    private String credentials;

    public Proxied(
        SSLSocketFactory sslContextFactory, HostnameVerifier hostnameVerifier, Proxy proxy) {
      super(sslContextFactory, hostnameVerifier);
      checkNotNull(proxy, "a proxy is required.");
      this.proxy = proxy;
    }

    public Proxied(
        SSLSocketFactory sslContextFactory,
        HostnameVerifier hostnameVerifier,
        Proxy proxy,
        String proxyUser,
        String proxyPassword) {
      this(sslContextFactory, hostnameVerifier, proxy);
      checkArgument(isNotBlank(proxyUser), "proxy user is required.");
      checkArgument(isNotBlank(proxyPassword), "proxy password is required.");
      this.credentials = basic(proxyUser, proxyPassword);
    }

    @Override
    public HttpURLConnection getConnection(URL url) throws IOException {
      HttpURLConnection connection = (HttpURLConnection) url.openConnection(this.proxy);
      if (isNotBlank(this.credentials)) {
        connection.addRequestProperty(PROXY_AUTHORIZATION, this.credentials);
      }
      return connection;
    }

    public String getCredentials() {
      return this.credentials;
    }

    private String basic(String username, String password) {
      String token = username + ":" + password;
      byte[] bytes = token.getBytes(StandardCharsets.ISO_8859_1);
      String encoded = Base64.getEncoder().encodeToString(bytes);
      return "Basic " + encoded;
    }
  }
}
