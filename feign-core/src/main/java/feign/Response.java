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

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static feign.Util.UTF_8;
import static feign.Util.checkNotNull;
import static feign.Util.checkState;
import static feign.Util.valuesOrEmpty;

/**
 * An immutable response to an http invocation which only returns string
 * content.
 */
public final class Response {
  private final int status;
  private final String reason;
  private final Map<String, Collection<String>> headers;
  private final Body body;

  public static Response create(int status, String reason, Map<String, Collection<String>> headers,
                                Reader chars, Integer length) {
    return new Response(status, reason, headers, ReaderBody.orNull(chars, length));
  }

  public static Response create(int status, String reason, Map<String, Collection<String>> headers, String chars) {
    return new Response(status, reason, headers, StringBody.orNull(chars));
  }

  private Response(int status, String reason, Map<String, Collection<String>> headers, Body body) {
    checkState(status >= 200, "Invalid status code: %s", status);
    this.status = status;
    this.reason = checkNotNull(reason, "reason");
    LinkedHashMap<String, Collection<String>> copyOf = new LinkedHashMap<String, Collection<String>>();
    copyOf.putAll(checkNotNull(headers, "headers"));
    this.headers = Collections.unmodifiableMap(copyOf);
    this.body = body; //nullable
  }

  /**
   * status code. ex {@code 200}
   *
   * See <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html" >rfc2616</a>
   */
  public int status() {
    return status;
  }

  public String reason() {
    return reason;
  }

  public Map<String, Collection<String>> headers() {
    return headers;
  }

  /**
   * if present, the response had a body
   */
  public Body body() {
    return body;
  }

  public interface Body extends Closeable {

    /**
     * length in bytes, if known. Null if not.
     * <br>
     * <br><br><b>Note</b><br> This is an integer as most implementations cannot do
     * bodies greater than 2GB. Moreover, the scope of this interface doesn't include
     * large bodies.
     */
    Integer length();

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
      return new ReaderBody(chars, length);
    }

    private final Reader chars;
    private final Integer length;

    private ReaderBody(Reader chars, Integer length) {
      this.chars = chars;
      this.length = length;
    }

    @Override public Integer length() {
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

    private volatile Integer length;

    @Override public Integer length() {
      if (length == null) {
        length = chars.getBytes(UTF_8).length;
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

  @Override public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("HTTP/1.1 ").append(status).append(' ').append(reason).append('\n');
    for (String field : headers.keySet()) {
      for (String value : valuesOrEmpty(headers, field)) {
        builder.append(field).append(": ").append(value).append('\n');
      }
    }
    if (body != null) {
      builder.append('\n').append(body);
    }
    return builder.toString();
  }
}
