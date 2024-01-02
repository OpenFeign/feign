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

import feign.Request.ProtocolVersion;
import java.util.Collection;
import java.util.Map;
import static feign.Util.*;

public final class TypedResponse<T> {

  private final int status;
  private final String reason;
  private final Map<String, Collection<String>> headers;
  private final T body;
  private final Request request;
  private final ProtocolVersion protocolVersion;

  private TypedResponse(Builder<T> builder) {
    checkState(builder.request != null, "original request is required");
    this.status = builder.status;
    this.request = builder.request;
    this.reason = builder.reason; // nullable
    this.headers = caseInsensitiveCopyOf(builder.headers);
    this.body = builder.body;
    this.protocolVersion = builder.protocolVersion;
  }

  public static <T> Builder<T> builder() {
    return new Builder<T>();
  }

  public static <T> Builder<T> builder(Response source) {
    return new Builder<T>(source);
  }

  public static final class Builder<T> {
    int status;
    String reason;
    Map<String, Collection<String>> headers;
    T body;
    Request request;
    private ProtocolVersion protocolVersion = ProtocolVersion.HTTP_1_1;

    Builder() {}

    Builder(Response source) {
      this.status = source.status();
      this.reason = source.reason();
      this.headers = source.headers();
      this.request = source.request();
      this.protocolVersion = source.protocolVersion();
    }

    /** @see TypedResponse#status */
    public Builder status(int status) {
      this.status = status;
      return this;
    }

    /** @see TypedResponse#reason */
    public Builder reason(String reason) {
      this.reason = reason;
      return this;
    }

    /** @see TypedResponse#headers */
    public Builder headers(Map<String, Collection<String>> headers) {
      this.headers = headers;
      return this;
    }

    /** @see TypedResponse#body */
    public Builder body(T body) {
      this.body = body;
      return this;
    }

    /**
     * @see TypedResponse#request
     */
    public Builder request(Request request) {
      checkNotNull(request, "request is required");
      this.request = request;
      return this;
    }

    /**
     * HTTP protocol version
     */
    public Builder protocolVersion(ProtocolVersion protocolVersion) {
      this.protocolVersion = protocolVersion;
      return this;
    }

    public TypedResponse build() {
      return new TypedResponse<T>(this);
    }
  }

  /**
   * status code. ex {@code 200}
   *
   * See <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html" >rfc2616</a>
   */
  public int status() {
    return status;
  }

  /**
   * Nullable and not set when using http/2
   *
   * See https://github.com/http2/http2-spec/issues/202
   */
  public String reason() {
    return reason;
  }

  /**
   * Returns a case-insensitive mapping of header names to their values.
   */
  public Map<String, Collection<String>> headers() {
    return headers;
  }

  /**
   * if present, the response had a body
   */
  public T body() {
    return body;
  }

  /**
   * the request that generated this response
   */
  public Request request() {
    return request;
  }

  /**
   * the HTTP protocol version
   *
   * @return HTTP protocol version or empty if a client does not provide it
   */
  public ProtocolVersion protocolVersion() {
    return protocolVersion;
  }

  @Override
  public String toString() {
    StringBuilder builder =
        new StringBuilder(protocolVersion.toString()).append(" ").append(status);
    if (reason != null)
      builder.append(' ').append(reason);
    builder.append('\n');
    for (String field : headers.keySet()) {
      for (String value : valuesOrEmpty(headers, field)) {
        builder.append(field).append(": ").append(value).append('\n');
      }
    }
    if (body != null)
      builder.append('\n').append(body);
    return builder.toString();
  }

}
