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

import static feign.Util.UTF_8;
import static feign.Util.checkNotNull;
import static feign.Util.checkState;
import static feign.Util.decodeOrDefault;
import static feign.Util.valuesOrEmpty;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** An immutable response to an http invocation which only returns string content. */
public final class Response {
  private final int status;
  private final String reason;
  private final Map<String, Collection<String>> headers;
  private final Body body;

  public static Response create(
      int status,
      String reason,
      Map<String, Collection<String>> headers,
      InputStream inputStream,
      Integer length) {
    return new Response(status, reason, headers, InputStreamBody.orNull(inputStream, length));
  }

  public static Response create(
      int status, String reason, Map<String, Collection<String>> headers, byte[] data) {
    return new Response(status, reason, headers, ByteArrayBody.orNull(data));
  }

  public static Response create(
      int status,
      String reason,
      Map<String, Collection<String>> headers,
      String text,
      Charset charset) {
    return new Response(status, reason, headers, ByteArrayBody.orNull(text, charset));
  }

  public static Response create(
      int status, String reason, Map<String, Collection<String>> headers, Body body) {
    return new Response(status, reason, headers, body);
  }

  private Response(int status, String reason, Map<String, Collection<String>> headers, Body body) {
    checkState(status >= 200, "Invalid status code: %s", status);
    this.status = status;
    this.reason = checkNotNull(reason, "reason");
    LinkedHashMap<String, Collection<String>> copyOf =
        new LinkedHashMap<String, Collection<String>>();
    copyOf.putAll(checkNotNull(headers, "headers"));
    this.headers = Collections.unmodifiableMap(copyOf);
    this.body = body; // nullable
  }

  /**
   * status code. ex {@code 200}
   *
   * <p>See <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html" >rfc2616</a>
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

  /** if present, the response had a body */
  public Body body() {
    return body;
  }

  public interface Body extends Closeable {

    /**
     * length in bytes, if known. Null if not. <br>
     * <br>
     * <br>
     * <b>Note</b><br>
     * This is an integer as most implementations cannot do bodies greater than 2GB. Moreover, the
     * scope of this interface doesn't include large bodies.
     */
    Integer length();

    /** True if {@link #asInputStream()} and {@link #asReader()} can be called more than once. */
    boolean isRepeatable();

    /** It is the responsibility of the caller to close the stream. */
    InputStream asInputStream() throws IOException;

    /** It is the responsibility of the caller to close the stream. */
    Reader asReader() throws IOException;
  }

  private static final class InputStreamBody implements Response.Body {
    private static Body orNull(InputStream inputStream, Integer length) {
      if (inputStream == null) {
        return null;
      }
      return new InputStreamBody(inputStream, length);
    }

    private final InputStream inputStream;
    private final Integer length;

    private InputStreamBody(InputStream inputStream, Integer length) {
      this.inputStream = inputStream;
      this.length = length;
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
      return inputStream;
    }

    @Override
    public Reader asReader() throws IOException {
      return new InputStreamReader(inputStream, UTF_8);
    }

    @Override
    public void close() throws IOException {
      inputStream.close();
    }
  }

  private static final class ByteArrayBody implements Response.Body {
    private static Body orNull(byte[] data) {
      if (data == null) {
        return null;
      }
      return new ByteArrayBody(data);
    }

    private static Body orNull(String text, Charset charset) {
      if (text == null) {
        return null;
      }
      checkNotNull(charset, "charset");
      return new ByteArrayBody(text.getBytes(charset));
    }

    private final byte[] data;

    public ByteArrayBody(byte[] data) {
      this.data = data;
    }

    @Override
    public Integer length() {
      return data.length;
    }

    @Override
    public boolean isRepeatable() {
      return true;
    }

    @Override
    public InputStream asInputStream() throws IOException {
      return new ByteArrayInputStream(data);
    }

    @Override
    public Reader asReader() throws IOException {
      return new InputStreamReader(asInputStream(), UTF_8);
    }

    @Override
    public void close() throws IOException {}

    @Override
    public String toString() {
      return decodeOrDefault(data, UTF_8, "Binary data");
    }
  }

  @Override
  public String toString() {
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
