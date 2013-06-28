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

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableListMultimap;

import java.util.Map.Entry;

import static com.google.common.base.Objects.equal;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An immutable request to an http server.
 * <p/>
 * <h4>Note</h4>
 * <p/>
 * Since {@link Feign} is designed for non-binary apis, and expectations are
 * that any request can be replayed, we only support a String body.
 */
public final class Request {

  private final String method;
  private final String url;
  private final ImmutableListMultimap<String, String> headers;
  private final Optional<String> body;

  Request(String method, String url, ImmutableListMultimap<String, String> headers, Optional<String> body) {
    this.method = checkNotNull(method, "method of %s", url);
    this.url = checkNotNull(url, "url");
    this.headers = checkNotNull(headers, "headers of %s %s", method, url);
    this.body = checkNotNull(body, "body of %s %s", method, url);
  }

  /* Method to invoke on the server. */
  public String method() {
    return method;
  }

  /* Fully resolved URL including query. */
  public String url() {
    return url;
  }

  /* Ordered list of headers that will be sent to the server. */
  public ImmutableListMultimap<String, String> headers() {
    return headers;
  }

  /* If present, this is the replayable body to send to the server. */
  public Optional<String> body() {
    return body;
  }

  /* Controls the per-request settings currently required to be implemented by all {@link Client clients} */
  public static class Options {

    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public Options(int connectTimeoutMillis, int readTimeoutMillis) {
      this.connectTimeoutMillis = connectTimeoutMillis;
      this.readTimeoutMillis = readTimeoutMillis;
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
  }

  @Override public int hashCode() {
    return Objects.hashCode(method, url, headers, body);
  }

  @Override public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (Request.class != obj.getClass())
      return false;
    Request that = Request.class.cast(obj);
    return equal(this.method, that.method) && equal(this.url, that.url) && equal(this.headers, that.headers)
        && equal(this.body, that.body);
  }

  @Override public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(method).append(' ').append(url).append(" HTTP/1.1\n");
    for (Entry<String, String> header : headers.entries()) {
      builder.append(header.getKey()).append(": ").append(header.getValue()).append('\n');
    }
    if (body.isPresent()) {
      builder.append('\n').append(body.get());
    }
    return builder.toString();
  }
}
