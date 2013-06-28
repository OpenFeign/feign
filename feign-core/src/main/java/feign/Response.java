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

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Map.Entry;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Objects.equal;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * An immutable response to an http invocation which only returns string
 * content.
 */
public final class Response {
  private final int status;
  private final String reason;
  private final ImmutableListMultimap<String, String> headers;
  private final Optional<Body> body;

  public static Response create(int status, String reason, ImmutableListMultimap<String, String> headers,
                                Reader chars, Integer length) {
    return new Response(status, reason, headers, Optional.fromNullable(ReaderBody.orNull(chars, length)));
  }

  public static Response create(int status, String reason, ImmutableListMultimap<String, String> headers, String chars) {
    return new Response(status, reason, headers, Optional.fromNullable(StringBody.orNull(chars)));
  }

  private Response(int status, String reason, ImmutableListMultimap<String, String> headers, Optional<Body> body) {
    checkState(status >= 200, "Invalid status code: %s", status);
    this.status = status;
    this.reason = checkNotNull(reason, "reason");
    this.headers = checkNotNull(headers, "headers");
    this.body = checkNotNull(body, "body");
  }

  /**
   * status code. ex {@code 200}
   *
   * @see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html" />
   */
  public int status() {
    return status;
  }

  public String reason() {
    return reason;
  }

  public ImmutableListMultimap<String, String> headers() {
    return headers;
  }

  public Optional<Body> body() {
    return body;
  }

  public interface Body extends Closeable {

    /**
     * length in bytes, if known.
     * <p/>
     * <h4>Note</h4> This is an integer as most implementations cannot do
     * bodies > 2GB. Moreover, the scope of this interface doesn't include
     * large bodies.
     */
    Optional<Integer> length();

    /**
     * True if {@link #asReader()} can be called more than once.
     */
    boolean isRepeatable();

    /**
     * It is the responsibility of the caller to close the stream.
     */
    Reader asReader() throws IOException;
  }

  private static final class ReaderBody implements Response.Body {
    private static Body orNull(Reader chars, Integer length) {
      if (chars == null)
        return null;
      return new ReaderBody(chars, Optional.fromNullable(length));
    }

    private final Reader chars;
    private final Optional<Integer> length;

    private ReaderBody(Reader chars, Optional<Integer> length) {
      this.chars = chars;
      this.length = length;
    }

    @Override public Optional<Integer> length() {
      return length;
    }

    @Override public boolean isRepeatable() {
      return false;
    }

    @Override public Reader asReader() throws IOException {
      return chars;
    }

    @Override public void close() throws IOException {
      chars.close();
    }
  }

  private static final class StringBody implements Response.Body {
    private static Body orNull(String chars) {
      if (chars == null)
        return null;
      return new StringBody(chars);
    }

    private final String chars;

    public StringBody(String chars) {
      this.chars = chars;
    }

    private volatile Optional<Integer> length;

    @Override public Optional<Integer> length() {
      if (length == null) {
        length = Optional.of(chars.getBytes(UTF_8).length);
      }
      return length;
    }

    @Override public boolean isRepeatable() {
      return true;
    }

    @Override public Reader asReader() throws IOException {
      return new StringReader(chars);
    }

    public String toString() {
      return chars;
    }

    @Override public void close() {
    }
  }

  @Override public int hashCode() {
    return Objects.hashCode(status, reason, headers, body);
  }

  @Override public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (Response.class != obj.getClass())
      return false;
    Response that = Response.class.cast(obj);
    return equal(this.status, that.status) && equal(this.reason, that.reason) && equal(this.headers, that.headers)
        && equal(this.body, that.body);
  }

  @Override public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("HTTP/1.1 ").append(status).append(' ').append(reason).append('\n');
    for (Entry<String, String> header : headers.entries()) {
      builder.append(header.getKey()).append(": ").append(header.getValue()).append('\n');
    }
    if (body.isPresent()) {
      builder.append('\n').append(body.get());
    }
    return builder.toString();
  }
}
