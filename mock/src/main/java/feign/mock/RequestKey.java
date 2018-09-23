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
package feign.mock;

import static feign.Util.UTF_8;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import feign.Request;
import feign.Util;

public class RequestKey {

  public static class Builder {

    private final HttpMethod method;

    private final String url;

    private RequestHeaders headers;

    private Charset charset;

    private byte[] body;

    private Builder(HttpMethod method, String url) {
      this.method = method;
      this.url = url;
    }

    @Deprecated
    public Builder headers(Map<String, Collection<String>> headers) {
      this.headers = RequestHeaders.of(headers);
      return this;
    }

    public Builder headers(RequestHeaders headers) {
      this.headers = headers;
      return this;
    }

    public Builder charset(Charset charset) {
      this.charset = charset;
      return this;
    }

    public Builder body(String body) {
      return body(body.getBytes(UTF_8));
    }

    public Builder body(byte[] body) {
      this.body = body;
      return this;
    }

    public RequestKey build() {
      return new RequestKey(this);
    }

  }

  public static Builder builder(HttpMethod method, String url) {
    return new Builder(method, url);
  }

  public static RequestKey create(Request request) {
    return new RequestKey(request);
  }

  private static String buildUrl(Request request) {
    try {
      return URLDecoder.decode(request.url(), Util.UTF_8.name());
    } catch (final UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  private final HttpMethod method;

  private final String url;

  private final RequestHeaders headers;

  private final Charset charset;

  private final byte[] body;

  private RequestKey(Builder builder) {
    this.method = builder.method;
    this.url = builder.url;
    this.headers = builder.headers;
    this.charset = builder.charset;
    this.body = builder.body;
  }

  private RequestKey(Request request) {
    this.method = HttpMethod.valueOf(request.httpMethod().name());
    this.url = buildUrl(request);
    this.headers = RequestHeaders.of(request.headers());
    this.charset = request.charset();
    this.body = request.body();
  }

  public HttpMethod getMethod() {
    return method;
  }

  public String getUrl() {
    return url;
  }

  public RequestHeaders getHeaders() {
    return headers;
  }

  public Charset getCharset() {
    return charset;
  }

  public byte[] getBody() {
    return body;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((method == null) ? 0 : method.hashCode());
    result = prime * result + ((url == null) ? 0 : url.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final RequestKey other = (RequestKey) obj;
    if (method != other.method) {
      return false;
    }
    if (url == null) {
      return other.url == null;
    } else
      return url.equals(other.url);
  }

  public boolean equalsExtended(Object obj) {
    if (equals(obj)) {
      RequestKey other = (RequestKey) obj;
      boolean headersEqual =
          other.headers == null || headers == null || headers.equals(other.headers);
      boolean charsetEqual =
          other.charset == null || charset == null || charset.equals(other.charset);
      boolean bodyEqual = other.body == null || body == null || Arrays.equals(other.body, body);
      return headersEqual && charsetEqual && bodyEqual;
    }
    return false;
  }

  @Override
  public String toString() {
    return String.format("Request [%s %s: %s headers and %s]", method, url,
        headers == null ? "without" : "with " + headers,
        charset == null ? "no charset" : "charset " + charset);
  }

}
