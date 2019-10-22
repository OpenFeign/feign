/**
 * Copyright 2012-2019 The Feign Authors
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign;

import static feign.Util.checkNotNull;
import static feign.Util.valuesOrEmpty;

import feign.template.BodyTemplate;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** An immutable request to an http server. */
public final class Request {

  public static class Body {

    private final byte[] data;
    private final Charset encoding;
    private final BodyTemplate bodyTemplate;

    private Body(byte[] data, Charset encoding, BodyTemplate bodyTemplate) {
      super();
      this.data = data;
      this.encoding = encoding;
      this.bodyTemplate = bodyTemplate;
    }

    public Request.Body expand(Map<String, ?> variables) {
      if (bodyTemplate == null) {
        return this;
      }

      return encoded(bodyTemplate.expand(variables).getBytes(encoding), encoding);
    }

    public List<String> getVariables() {
      if (bodyTemplate == null) {
        return Collections.emptyList();
      }
      return bodyTemplate.getVariables();
    }

    public static Request.Body encoded(byte[] bodyData, Charset encoding) {
      return new Request.Body(bodyData, encoding, null);
    }

    public int length() {
      /* calculate the content length based on the data provided */
      return data != null ? data.length : 0;
    }

    public byte[] asBytes() {
      return data;
    }

    public static Request.Body bodyTemplate(String bodyTemplate, Charset encoding) {
      return new Request.Body(null, encoding, BodyTemplate.create(bodyTemplate));
    }

    public String bodyTemplate() {
      return (bodyTemplate != null) ? bodyTemplate.toString() : null;
    }

    public String asString() {
      return !isBinary() ? new String(data, encoding) : "Binary data";
    }

    public static Body empty() {
      return new Request.Body(null, null, null);
    }

    public boolean isBinary() {
      return encoding == null || data == null;
    }
  }

  public enum HttpMethod {
    GET,
    HEAD,
    POST,
    PUT,
    DELETE,
    CONNECT,
    OPTIONS,
    TRACE,
    PATCH
  }

  /**
   * No parameters can be null except {@code body} and {@code charset}. All parameters must be
   * effectively immutable, via safe copies, not mutating or otherwise.
   *
   * @deprecated {@link #create(HttpMethod, String, Map, byte[], Charset)}
   */
  @Deprecated
  public static Request create(
      String method,
      String url,
      Map<String, Collection<String>> headers,
      byte[] body,
      Charset charset) {
    checkNotNull(method, "httpMethod of %s", method);
    final HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());
    return create(httpMethod, url, headers, body, charset, null);
  }

  /**
   * Builds a Request. All parameters must be effectively immutable, via safe copies.
   *
   * @param httpMethod for the request.
   * @param url for the request.
   * @param headers to include.
   * @param body of the request, can be {@literal null}
   * @param charset of the request, can be {@literal null}
   * @return a Request
   */
  @Deprecated
  public static Request create(
      HttpMethod httpMethod,
      String url,
      Map<String, Collection<String>> headers,
      byte[] body,
      Charset charset) {
    return create(httpMethod, url, headers, Body.encoded(body, charset), null);
  }

  /**
   * Builds a Request. All parameters must be effectively immutable, via safe copies.
   *
   * @param httpMethod for the request.
   * @param url for the request.
   * @param headers to include.
   * @param body of the request, can be {@literal null}
   * @param charset of the request, can be {@literal null}
   * @return a Request
   */
  public static Request create(
      HttpMethod httpMethod,
      String url,
      Map<String, Collection<String>> headers,
      byte[] body,
      Charset charset,
      RequestTemplate requestTemplate) {
    return create(httpMethod, url, headers, Body.encoded(body, charset), requestTemplate);
  }

  /**
   * Builds a Request. All parameters must be effectively immutable, via safe copies.
   *
   * @param httpMethod for the request.
   * @param url for the request.
   * @param headers to include.
   * @param body of the request, can be {@literal null}
   * @return a Request
   */
  public static Request create(
      HttpMethod httpMethod,
      String url,
      Map<String, Collection<String>> headers,
      Body body,
      RequestTemplate requestTemplate) {
    return new Request(httpMethod, url, headers, body, requestTemplate);
  }

  private final HttpMethod httpMethod;
  private final String url;
  private final Map<String, Collection<String>> headers;
  private final Body body;
  private final RequestTemplate requestTemplate;

  Request(
      HttpMethod method,
      String url,
      Map<String, Collection<String>> headers,
      Body body,
      RequestTemplate requestTemplate) {
    this.httpMethod = checkNotNull(method, "httpMethod of %s", method.name());
    this.url = checkNotNull(url, "url");
    this.headers = checkNotNull(headers, "headers of %s %s", method, url);
    this.body = body;
    this.requestTemplate = requestTemplate;
  }

  /**
   * Http Method for this request.
   *
   * @return the HttpMethod string
   * @deprecated @see {@link #httpMethod()}
   */
  @Deprecated
  public String method() {
    return httpMethod.name();
  }

  /**
   * Http Method for the request.
   *
   * @return the HttpMethod.
   */
  public HttpMethod httpMethod() {
    return this.httpMethod;
  }

  /* Fully resolved URL including query. */
  public String url() {
    return url;
  }

  /* Ordered list of headers that will be sent to the server. */
  public Map<String, Collection<String>> headers() {
    return headers;
  }

  /**
   * The character set with which the body is encoded, or null if unknown or not applicable. When
   * this is present, you can use {@code new String(req.body(), req.charset())} to access the body
   * as a String.
   *
   * @deprecated use {@link #requestBody()} instead
   */
  @Deprecated
  public Charset charset() {
    return body.encoding;
  }

  /**
   * If present, this is the replayable body to send to the server. In some cases, this may be
   * interpretable as text.
   *
   * @see #charset()
   * @deprecated use {@link #requestBody()} instead
   */
  @Deprecated
  public byte[] body() {
    return body.data;
  }

  public Body requestBody() {
    return body;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append(httpMethod).append(' ').append(url).append(" HTTP/1.1\n");
    for (final String field : headers.keySet()) {
      for (final String value : valuesOrEmpty(headers, field)) {
        builder.append(field).append(": ").append(value).append('\n');
      }
    }
    if (body != null) {
      builder.append('\n').append(body.asString());
    }
    return builder.toString();
  }

  /*
   * Controls the per-request settings currently required to be implemented by all {@link Client
   * clients}
   */
  public static class Options {

    private final long connectTimeout;
    private final TimeUnit connectTimeoutUnit;
    private final long readTimeout;
    private final TimeUnit readTimeoutUnit;
    private final boolean followRedirects;

    public Options(
        long connectTimeout,
        TimeUnit connectTimeoutUnit,
        long readTimeout,
        TimeUnit readTimeoutUnit,
        boolean followRedirects) {
      super();
      this.connectTimeout = connectTimeout;
      this.connectTimeoutUnit = connectTimeoutUnit;
      this.readTimeout = readTimeout;
      this.readTimeoutUnit = readTimeoutUnit;
      this.followRedirects = followRedirects;
    }

    @Deprecated
    public Options(int connectTimeoutMillis, int readTimeoutMillis, boolean followRedirects) {
      this(
          connectTimeoutMillis,
          TimeUnit.MILLISECONDS,
          readTimeoutMillis,
          TimeUnit.MILLISECONDS,
          followRedirects);
    }

    @Deprecated
    public Options(int connectTimeoutMillis, int readTimeoutMillis) {
      this(connectTimeoutMillis, readTimeoutMillis, true);
    }

    public Options() {
      this(10, TimeUnit.SECONDS, 60, TimeUnit.SECONDS, true);
    }

    /**
     * Defaults to 10 seconds. {@code 0} implies no timeout.
     *
     * @see java.net.HttpURLConnection#getConnectTimeout()
     */
    @Deprecated
    public int connectTimeoutMillis() {
      return (int) connectTimeoutUnit.toMillis(connectTimeout);
    }

    /**
     * Defaults to 60 seconds. {@code 0} implies no timeout.
     *
     * @see java.net.HttpURLConnection#getReadTimeout()
     */
    @Deprecated
    public int readTimeoutMillis() {
      return (int) readTimeoutUnit.toMillis(readTimeout);
    }

    /**
     * Defaults to true. {@code false} tells the client to not follow the redirections.
     *
     * @see HttpURLConnection#getFollowRedirects()
     */
    public boolean isFollowRedirects() {
      return followRedirects;
    }

    public long connectTimeout() {
      return connectTimeout;
    }

    public TimeUnit connectTimeoutUnit() {
      return connectTimeoutUnit;
    }

    public long readTimeout() {
      return readTimeout;
    }

    public TimeUnit readTimeoutUnit() {
      return readTimeoutUnit;
    }
  }

  @Experimental
  public RequestTemplate requestTemplate() {
    return this.requestTemplate;
  }
}
