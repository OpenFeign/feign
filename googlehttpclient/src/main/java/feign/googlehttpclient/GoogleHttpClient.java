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
package feign.googlehttpclient;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import feign.Client;
import feign.Request;
import feign.Response;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * This module directs Feign's http requests to
 * <a href="https://developers.google.com/api-client-library/java/google-http-java-client/">Google
 * HTTP Client</a>.
 *
 * <pre>
 * GitHub github = Feign.builder().client(new GoogleHttpCliest()).target(GitHub.class,
 * "https://api.github.com");
 */
public class GoogleHttpClient implements Client {
  private final HttpRequestFactory requestFactory;

  public GoogleHttpClient() {
    this(new NetHttpTransport());
  }

  public GoogleHttpClient(final HttpTransport transport) {
    this(transport.createRequestFactory());
  }

  public GoogleHttpClient(final HttpRequestFactory requestFactory) {
    this.requestFactory = requestFactory;
  }

  @Override
  public final Response execute(final Request inputRequest, final Request.Options options)
      throws IOException {
    final HttpRequest request = convertRequest(inputRequest, options);
    final HttpResponse response = request.execute();
    return convertResponse(inputRequest, response);
  }

  private final HttpRequest convertRequest(
      final Request inputRequest, final Request.Options options) throws IOException {
    // Setup the request body
    HttpContent content = null;
    if (inputRequest.length() > 0) {
      String contentType = "application/octet-stream";
      for (final Map.Entry<String, Collection<String>> header : inputRequest.headers().entrySet()) {
        if (isContentType(header.getKey())
            && header.getValue() != null
            && !header.getValue().isEmpty()) {
          contentType = header.getValue().iterator().next();
          break;
        }
      }
      content = new ByteArrayContent(contentType, inputRequest.body());
    }

    // Build the request
    final HttpRequest request =
        requestFactory.buildRequest(
            inputRequest.httpMethod().name(), new GenericUrl(inputRequest.url()), content);
    // Setup headers
    final HttpHeaders headers = new HttpHeaders();
    for (final Map.Entry<String, Collection<String>> header : inputRequest.headers().entrySet()) {
      // We already set the Content-Type header via ByteArrayContent
      // Content-Type is defined as a singleton field
      // https://www.rfc-editor.org/rfc/rfc9110.html#section-8.3-7
      if (!isContentType(header.getKey())) {
        headers.set(header.getKey(), header.getValue());
      }
    }
    // Some servers don't do well with no Accept header
    if (inputRequest.headers().get("Accept") == null) {
      headers.setAccept("*/*");
    }
    request.setHeaders(headers);

    // Setup request options
    request
        .setReadTimeout(options.readTimeoutMillis())
        .setConnectTimeout(options.connectTimeoutMillis())
        .setFollowRedirects(options.isFollowRedirects())
        .setThrowExceptionOnExecuteError(false);
    return request;
  }

  // Header names are case-insensitive, and Request does not guarantee a case-insensitive map
  private static boolean isContentType(final String headerName) {
    return "Content-Type".equalsIgnoreCase(headerName);
  }

  private final Response convertResponse(
      final Request inputRequest, final HttpResponse inputResponse) throws IOException {
    final HttpHeaders headers = inputResponse.getHeaders();
    final Long length = headers.getContentLength();
    Integer contentLength = null;
    if (length != null && length >= 0 && length <= Integer.MAX_VALUE) {
      contentLength = length.intValue();
    }
    return Response.builder()
        .body(inputResponse.getContent(), contentLength)
        .status(inputResponse.getStatusCode())
        .reason(inputResponse.getStatusMessage())
        .headers(toMap(inputResponse.getHeaders()))
        .request(inputRequest)
        .build();
  }

  private final Map<String, Collection<String>> toMap(final HttpHeaders headers) {
    final Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    for (final String header : headers.keySet()) {
      map.put(header, headers.getHeaderStringValues(header));
    }
    return map;
  }
}
