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
package feign.httpclient;

import feign.Client;
import feign.Request;
import feign.Response;
import feign.Util;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This module directs Feign's http requests to Apache's
 * <a href="https://hc.apache.org/httpcomponents-client-ga/">HttpClient</a>. Ex.
 * <pre>
 * GitHub github = Feign.builder().client(new ApacheHttpClient()).target(GitHub.class,
 * "https://api.github.com");
 */
/*
 * Based on Square, Inc's Retrofit ApacheClient implementation
 */
public final class ApacheHttpClient implements Client {
  private static final String ACCEPT_HEADER_NAME = "Accept";

  private final HttpClient client;

  public ApacheHttpClient() {
    this(HttpClientBuilder.create().build());
  }

  public ApacheHttpClient(HttpClient client) {
    this.client = client;
  }

  @Override
  public Response execute(Request request, Request.Options options) throws IOException {
    HttpUriRequest httpUriRequest;
    try {
      httpUriRequest = toHttpUriRequest(request, options);
    } catch (URISyntaxException e) {
      throw new IOException("URL '" + request.url() + "' couldn't be parsed into a URI", e);
    }
    HttpResponse httpResponse = client.execute(httpUriRequest);
    return toFeignResponse(httpResponse);
  }

  HttpUriRequest toHttpUriRequest(Request request, Request.Options options) throws
          UnsupportedEncodingException, MalformedURLException, URISyntaxException {
    RequestBuilder requestBuilder = RequestBuilder.create(request.method());

    //per request timeouts
    RequestConfig requestConfig = RequestConfig
            .custom()
            .setConnectTimeout(options.connectTimeoutMillis())
            .setSocketTimeout(options.readTimeoutMillis())
            .build();
    requestBuilder.setConfig(requestConfig);

    URI uri = new URIBuilder(request.url()).build();

    //request url
    requestBuilder.setUri(uri.getScheme() + "://" + uri.getAuthority() + uri.getPath());

    //request query params
    List<NameValuePair> queryParams = URLEncodedUtils.parse(uri, requestBuilder.getCharset().name());
    for (NameValuePair queryParam: queryParams) {
      requestBuilder.addParameter(queryParam);
    }

    //request body
    if (request.body() != null) {
      HttpEntity entity = request.charset() != null ?
              new StringEntity(new String(request.body(), request.charset())) :
              new ByteArrayEntity(request.body());
      requestBuilder.setEntity(entity);
    }

    //request headers
    boolean hasAcceptHeader = false;
    for (Map.Entry<String, Collection<String>> headerEntry : request.headers().entrySet()) {
      String headerName = headerEntry.getKey();
      if (headerName.equalsIgnoreCase(ACCEPT_HEADER_NAME)) {
        hasAcceptHeader = true;
      }
      if (headerName.equalsIgnoreCase(Util.CONTENT_LENGTH) &&
              requestBuilder.getHeaders(headerName) != null) {
        //if the 'Content-Length' header is already present, it's been set from HttpEntity, so we
        //won't add it again
        continue;
      }

      for (String headerValue : headerEntry.getValue()) {
        requestBuilder.addHeader(headerName, headerValue);
      }
    }
    //some servers choke on the default accept string, so we'll set it to anything
    if (!hasAcceptHeader) {
      requestBuilder.addHeader(ACCEPT_HEADER_NAME, "*/*");
    }

    return requestBuilder.build();
  }

  Response toFeignResponse(HttpResponse httpResponse) throws IOException {
    StatusLine statusLine = httpResponse.getStatusLine();
    int statusCode = statusLine.getStatusCode();

    String reason = statusLine.getReasonPhrase();

    Map<String, Collection<String>> headers = new HashMap<String, Collection<String>>();
    for (Header header : httpResponse.getAllHeaders()) {
      String name = header.getName();
      String value = header.getValue();

      Collection<String> headerValues = headers.get(name);
      if (headerValues == null) {
        headerValues = new ArrayList<String>();
        headers.put(name, headerValues);
      }
      headerValues.add(value);
    }

    return Response.create(statusCode, reason, headers, toFeignBody(httpResponse));
  }

  Response.Body toFeignBody(HttpResponse httpResponse) throws IOException {
    HttpEntity entity = httpResponse.getEntity();
    final Integer length = entity != null && entity.getContentLength() != -1 ?
            (int) entity.getContentLength() :
            null;
    final InputStream input = entity != null ?
            new ByteArrayInputStream(EntityUtils.toByteArray(entity)) :
            null;

    return new Response.Body() {

      @Override
      public void close() throws IOException {
        if (input != null) {
          input.close();
        }
      }

      @Override
      public Integer length() {
        return length;
      }

      @Override
      public boolean isRepeatable() {
        return false;
      }

      @Override
      public InputStream asInputStream() throws IOException {
        return input;
      }

      @Override
      public Reader asReader() throws IOException {
        return new InputStreamReader(input);
      }
    };
  }

}
