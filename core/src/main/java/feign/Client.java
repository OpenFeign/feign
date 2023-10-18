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
package feign;

import static feign.Util.CONTENT_ENCODING;
import static feign.Util.ACCEPT_ENCODING;
import static feign.Util.CONTENT_LENGTH;
import static feign.Util.ENCODING_DEFLATE;
import static feign.Util.ENCODING_GZIP;
import static feign.Util.checkArgument;
import static feign.Util.checkNotNull;
import static feign.Util.isNotBlank;
import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.lang.String.format;
import feign.Request.Options;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

/**
 * Submits HTTP {@link Request requests}. Implementations are expected to be thread-safe.
 */
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

  class Default implements Client {

    private final SSLSocketFactory sslContextFactory;
    private final HostnameVerifier hostnameVerifier;

    /**
     * Disable the request body internal buffering for {@code HttpURLConnection}.
     *
     * @see HttpURLConnection#setFixedLengthStreamingMode(int)
     * @see HttpURLConnection#setFixedLengthStreamingMode(long)
     * @see HttpURLConnection#setChunkedStreamingMode(int)
     */
    private final boolean disableRequestBuffering;

    /**
     * Create a new client, which disable request buffering by default.
     *
     * @param sslContextFactory SSLSocketFactory for secure https URL connections.
     * @param hostnameVerifier the host name verifier.
     */
    public Default(SSLSocketFactory sslContextFactory, HostnameVerifier hostnameVerifier) {
      this.sslContextFactory = sslContextFactory;
      this.hostnameVerifier = hostnameVerifier;
      this.disableRequestBuffering = true;
    }

    /**
     * Create a new client.
     *
     * @param sslContextFactory SSLSocketFactory for secure https URL connections.
     * @param hostnameVerifier the host name verifier.
     * @param disableRequestBuffering Disable the request body internal buffering for
     *        {@code HttpURLConnection}.
     */
    public Default(SSLSocketFactory sslContextFactory, HostnameVerifier hostnameVerifier,
        boolean disableRequestBuffering) {
      super();
      this.sslContextFactory = sslContextFactory;
      this.hostnameVerifier = hostnameVerifier;
      this.disableRequestBuffering = disableRequestBuffering;
    }

    @Override
    public Response execute(Request request, Options options) throws IOException {
      HttpURLConnection connection = convertAndSend(request, options);
      return convertResponse(connection, request);
    }

    Response convertResponse(HttpURLConnection connection, Request request) throws IOException {
      int status = connection.getResponseCode();
      String reason = connection.getResponseMessage();

      if (status < 0) {
        throw new IOException(format("Invalid status(%s) executing %s %s", status,
            connection.getRequestMethod(), connection.getURL()));
      }

      Map<String, Collection<String>> headers = new TreeMap<>(CASE_INSENSITIVE_ORDER);
      for (Map.Entry<String, List<String>> field : connection.getHeaderFields().entrySet()) {
        // response message
        if (field.getKey() != null) {
          headers.put(field.getKey(), field.getValue());
        }
      }

      Integer length = connection.getContentLength();
      if (length == -1) {
        length = null;
      }
      InputStream stream;
      if (status >= 400) {
        stream = connection.getErrorStream();
      } else {
        stream = connection.getInputStream();
      }
      if (this.isGzip(headers.get(CONTENT_ENCODING))) {
        stream = new GZIPInputStream(stream);
      } else if (this.isDeflate(headers.get(CONTENT_ENCODING))) {
        stream = new InflaterInputStream(stream);
      }
      return Response.builder()
          .status(status)
          .reason(reason)
          .headers(headers)
          .request(request)
          .body(stream, length)
          .build();
    }

    public HttpURLConnection getConnection(final URL url) throws IOException {
      return (HttpURLConnection) url.openConnection();
    }

    HttpURLConnection convertAndSend(Request request, Options options) throws IOException {
      final URL url = new URL(request.url());
      final HttpURLConnection connection = this.getConnection(url);
      if (connection instanceof HttpsURLConnection) {
        HttpsURLConnection sslCon = (HttpsURLConnection) connection;
        if (sslContextFactory != null) {
          sslCon.setSSLSocketFactory(sslContextFactory);
        }
        if (hostnameVerifier != null) {
          sslCon.setHostnameVerifier(hostnameVerifier);
        }
      }
      connection.setConnectTimeout(options.connectTimeoutMillis());
      connection.setReadTimeout(options.readTimeoutMillis());
      connection.setAllowUserInteraction(false);
      connection.setInstanceFollowRedirects(options.isFollowRedirects());
      connection.setRequestMethod(request.httpMethod().name());

      Collection<String> contentEncodingValues = request.headers().get(CONTENT_ENCODING);
      boolean gzipEncodedRequest = this.isGzip(contentEncodingValues);
      boolean deflateEncodedRequest = this.isDeflate(contentEncodingValues);

      boolean hasAcceptHeader = false;
      Integer contentLength = null;
      for (String field : request.headers().keySet()) {
        if (field.equalsIgnoreCase("Accept")) {
          hasAcceptHeader = true;
        }
        for (String value : request.headers().get(field)) {
          if (field.equals(CONTENT_LENGTH)) {
            if (!gzipEncodedRequest && !deflateEncodedRequest) {
              contentLength = Integer.valueOf(value);
              connection.addRequestProperty(field, value);
            }
          }
          // Avoid add "Accept-encoding" twice or more when "compression" option is enabled
          if (field.equals(ACCEPT_ENCODING)) {
            connection.addRequestProperty(field, String.join(", ", request.headers().get(field)));
            break;
          } else {
            connection.addRequestProperty(field, value);
          }
        }
      }
      // Some servers choke on the default accept string.
      if (!hasAcceptHeader) {
        connection.addRequestProperty("Accept", "*/*");
      }

      boolean hasEmptyBody = false;
      byte[] body = request.body();
      if (body == null && request.httpMethod().isWithBody()) {
        body = new byte[0];
        hasEmptyBody = true;
      }

      if (body != null) {
        /*
         * Ignore disableRequestBuffering flag if the empty body was set, to ensure that internal
         * retry logic applies to such requests.
         */
        if (disableRequestBuffering && !hasEmptyBody) {
          if (contentLength != null) {
            connection.setFixedLengthStreamingMode(contentLength);
          } else {
            connection.setChunkedStreamingMode(8196);
          }
        }
        connection.setDoOutput(true);
        OutputStream out = connection.getOutputStream();
        if (gzipEncodedRequest) {
          out = new GZIPOutputStream(out);
        } else if (deflateEncodedRequest) {
          out = new DeflaterOutputStream(out);
        }
        try {
          out.write(body);
        } finally {
          try {
            out.close();
          } catch (IOException suppressed) { // NOPMD
          }
        }
      }
      return connection;
    }

    private boolean isGzip(Collection<String> contentEncodingValues) {
      return contentEncodingValues != null
          && !contentEncodingValues.isEmpty()
          && contentEncodingValues.contains(ENCODING_GZIP);
    }

    private boolean isDeflate(Collection<String> contentEncodingValues) {
      return contentEncodingValues != null
          && !contentEncodingValues.isEmpty()
          && contentEncodingValues.contains(ENCODING_DEFLATE);
    }
  }

  /**
   * Client that supports a {@link java.net.Proxy}.
   */
  class Proxied extends Default {

    public static final String PROXY_AUTHORIZATION = "Proxy-Authorization";
    private final Proxy proxy;
    private String credentials;

    public Proxied(SSLSocketFactory sslContextFactory, HostnameVerifier hostnameVerifier,
        Proxy proxy) {
      super(sslContextFactory, hostnameVerifier);
      checkNotNull(proxy, "a proxy is required.");
      this.proxy = proxy;
    }

    public Proxied(SSLSocketFactory sslContextFactory, HostnameVerifier hostnameVerifier,
        Proxy proxy, String proxyUser, String proxyPassword) {
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
