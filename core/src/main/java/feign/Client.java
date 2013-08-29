/*
 * Copyright 2013 Netflix, Inc.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import javax.inject.Inject;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import dagger.Lazy;
import feign.Request.Options;

import static feign.Util.CONTENT_ENCODING;
import static feign.Util.CONTENT_LENGTH;
import static feign.Util.ENCODING_GZIP;
import static feign.Util.UTF_8;

/**
 * Submits HTTP {@link Request requests}. Implementations are expected to be
 * thread-safe.
 */
public interface Client {
  /**
   * Executes a request against its {@link Request#url() url} and returns a
   * response.
   *
   * @param request safe to replay.
   * @param options options to apply to this request.
   * @return connected response, {@link Response.Body} is absent or unread.
   * @throws IOException on a network error connecting to {@link Request#url()}.
   */
  Response execute(Request request, Options options) throws IOException;

  public static class Default implements Client {
    private final Lazy<SSLSocketFactory> sslContextFactory;
    private final Lazy<HostnameVerifier> hostnameVerifier;

    @Inject public Default(Lazy<SSLSocketFactory> sslContextFactory, Lazy<HostnameVerifier> hostnameVerifier) {
      this.sslContextFactory = sslContextFactory;
      this.hostnameVerifier = hostnameVerifier;
    }

    @Override public Response execute(Request request, Options options) throws IOException {
      HttpURLConnection connection = convertAndSend(request, options);
      return convertResponse(connection);
    }

    HttpURLConnection convertAndSend(Request request, Options options) throws IOException {
      final HttpURLConnection connection = (HttpURLConnection) new URL(request.url()).openConnection();
      if (connection instanceof HttpsURLConnection) {
        HttpsURLConnection sslCon = (HttpsURLConnection) connection;
        sslCon.setSSLSocketFactory(sslContextFactory.get());
        sslCon.setHostnameVerifier(hostnameVerifier.get());
      }
      connection.setConnectTimeout(options.connectTimeoutMillis());
      connection.setReadTimeout(options.readTimeoutMillis());
      connection.setAllowUserInteraction(false);
      connection.setInstanceFollowRedirects(true);
      connection.setRequestMethod(request.method());

      Collection<String> contentEncodingValues = request.headers().get(CONTENT_ENCODING);
      boolean gzipEncodedRequest = contentEncodingValues != null && contentEncodingValues.contains(ENCODING_GZIP);

      Integer contentLength = null;
      for (String field : request.headers().keySet()) {
        for (String value : request.headers().get(field)) {
          if (field.equals(CONTENT_LENGTH)) {
            if (!gzipEncodedRequest) {
              contentLength = Integer.valueOf(value);
              connection.addRequestProperty(field, value);
            }
          } else {
            connection.addRequestProperty(field, value);
          }
        }
      }

      if (request.body() != null) {
        if (contentLength != null) {
          connection.setFixedLengthStreamingMode(contentLength);
        } else {
          connection.setChunkedStreamingMode(8196);
        }
        connection.setDoOutput(true);
        OutputStream out = connection.getOutputStream();
        if (gzipEncodedRequest) {
          out = new GZIPOutputStream(out);
        }
        try {
          out.write(request.body().getBytes(UTF_8));
        } finally {
          try {
            out.close();
          } catch (IOException suppressed) { // NOPMD
          }
        }
      }
      return connection;
    }

    Response convertResponse(HttpURLConnection connection) throws IOException {
      int status = connection.getResponseCode();
      String reason = connection.getResponseMessage();

      Map<String, Collection<String>> headers = new LinkedHashMap<String, Collection<String>>();
      for (Map.Entry<String, List<String>> field : connection.getHeaderFields().entrySet()) {
        // response message
        if (field.getKey() != null)
          headers.put(field.getKey(), field.getValue());
      }

      Integer length = connection.getContentLength();
      if (length == -1)
        length = null;
      InputStream stream;
      if (status >= 400) {
        stream = connection.getErrorStream();
      } else {
        stream = connection.getInputStream();
      }
      Reader body = stream != null ? new InputStreamReader(stream) : null;
      return Response.create(status, reason, headers, body, length);
    }
  }
}
