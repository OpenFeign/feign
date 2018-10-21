/**
 * Copyright 2012-2018 The Feign Authors
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

import static feign.Util.checkNotNull;
import static feign.Util.valuesOrEmpty;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.util.*;
import feign.template.BodyTemplate;

/**
 * An immutable request to an http server.
 */
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
      if (bodyTemplate == null)
        return this;

      return encoded(bodyTemplate.expand(variables).getBytes(encoding), encoding);
    }

    public List<String> getVariables() {
      if (bodyTemplate == null)
        return Collections.emptyList();
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
      return encoding != null && data != null
          ? new String(data, encoding)
          : "Binary data";
    }

    public static Body empty() {
      return new Request.Body(null, null, null);
    }

  }

  public enum HttpMethod {
    GET, HEAD, POST, PUT, DELETE, CONNECT, OPTIONS, TRACE, PATCH
  }

  /**
   * No parameters can be null except {@code body} and {@code charset}. All parameters must be
   * effectively immutable, via safe copies, not mutating or otherwise.
   *
   * @deprecated {@link #create(HttpMethod, String, Map, byte[], Charset)}
   */
  public static Request create(String method,
                               String url,
                               Map<String, Collection<String>> headers,
                               byte[] body,
                               Charset charset) {
    checkNotNull(method, "httpMethod of %s", method);
    HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());
    return create(httpMethod, url, headers, body, charset);
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
  public static Request create(HttpMethod httpMethod,
                               String url,
                               Map<String, Collection<String>> headers,
                               byte[] body,
                               Charset charset) {
    return create(httpMethod, url, headers, Body.encoded(body, charset));
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
  public static Request create(HttpMethod httpMethod,
                               String url,
                               Map<String, Collection<String>> headers,
                               Body body) {
    return new Request(httpMethod, url, headers, body);
  }

  private final HttpMethod httpMethod;
  private final String url;
  private final Map<String, Collection<String>> headers;
  private final Body body;

  Request(HttpMethod method, String url, Map<String, Collection<String>> headers, Body body) {
    this.httpMethod = checkNotNull(method, "httpMethod of %s", method.name());
    this.url = checkNotNull(url, "url");
    this.headers = checkNotNull(headers, "headers of %s %s", method, url);
    this.body = body;
  }

  /**
   * Http Method for this request.
   *
   * @return the HttpMethod string
   * @deprecated @see {@link #httpMethod()}
   */
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
  public byte[] body() {
    return body.data;
  }

  public Body requestBody() {
    return body;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(httpMethod).append(' ').append(url).append(" HTTP/1.1\n");
    for (String field : headers.keySet()) {
      for (String value : valuesOrEmpty(headers, field)) {
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

    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final boolean followRedirects;

    public Options(int connectTimeoutMillis, int readTimeoutMillis, boolean followRedirects) {
      this.connectTimeoutMillis = connectTimeoutMillis;
      this.readTimeoutMillis = readTimeoutMillis;
      this.followRedirects = followRedirects;
    }

    public Options(int connectTimeoutMillis, int readTimeoutMillis) {
      this(connectTimeoutMillis, readTimeoutMillis, true);
    }

    public Options() {
      this(10 * 1000, 60 * 1000);
    }

    /**
     * Defaults to 10 seconds. {@code 0} implies no timeout.
     *
     * @see java.net.HttpURLConnection#getConnectTimeout()
     */
    public int connectTimeoutMillis() {
      return connectTimeoutMillis;
    }

    /**
     * Defaults to 60 seconds. {@code 0} implies no timeout.
     *
     * @see java.net.HttpURLConnection#getReadTimeout()
     */
    public int readTimeoutMillis() {
      return readTimeoutMillis;
    }


    /**
     * Defaults to true. {@code false} tells the client to not follow the redirections.
     *
     * @see HttpURLConnection#getFollowRedirects()
     */
    public boolean isFollowRedirects() {
      return followRedirects;
    }
  }
}
