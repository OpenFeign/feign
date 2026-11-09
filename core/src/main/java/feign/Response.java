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
package feign;

import static feign.Util.*;

import feign.Request.ProtocolVersion;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.*;

/** An immutable response to an http invocation which only returns string content. */
public final class Response implements Closeable {

  private final int status;
  private final String reason;
  private final Map<String, Collection<String>> headers;
  private final Body body;
  private final Request request;
  private final ProtocolVersion protocolVersion;

  private Response(Builder builder) {
    checkState(builder.request != null, "original request is required");
    this.status = builder.status;
    this.request = builder.request;
    this.reason = builder.reason; // nullable
    this.headers = caseInsensitiveCopyOf(builder.headers);
    this.body = builder.body; // nullable
    this.protocolVersion = builder.protocolVersion;
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private static final ProtocolVersion DEFAULT_PROTOCOL_VERSION = ProtocolVersion.HTTP_1_1;
    private int status;
    private String reason;
    private Map<String, Collection<String>> headers;
    private Body body;
    private Request request;
    private RequestTemplate requestTemplate;
    private ProtocolVersion protocolVersion = DEFAULT_PROTOCOL_VERSION;

    Builder() {}

    Builder(Response source) {
      this.status = source.status;
      this.reason = source.reason;
      this.headers = source.headers;
      this.body = source.body;
      this.request = source.request;
      this.protocolVersion = source.protocolVersion;
    }

    /**
     * @see Response#status
     */
    public Builder status(int status) {
      this.status = status;
      return this;
    }

    /**
     * @see Response#reason
     */
    public Builder reason(String reason) {
      this.reason = reason;
      return this;
    }

    /**
     * @see Response#headers
     */
    public Builder headers(Map<String, Collection<String>> headers) {
      this.headers = headers;
      return this;
    }

    /**
     * @see Response#body
     */
    public Builder body(Body body) {
      this.body = body;
      return this;
    }

    /**
     * @see Response#body
     */
    public Builder body(InputStream inputStream, Integer length) {
      this.body = InputStreamBody.orNull(inputStream, length);
      return this;
    }

    /**
     * @see Response#body
     */
    public Builder body(byte[] data) {
      this.body = ByteArrayBody.orNull(data);
      return this;
    }

    /**
     * @see Response#body
     */
    public Builder body(String text, Charset charset) {
      this.body = ByteArrayBody.orNull(text, charset);
      return this;
    }

    /**
     * @see Response#request
     */
    public Builder request(Request request) {
      checkNotNull(request, "request is required");
      this.request = request;
      return this;
    }

    /** HTTP protocol version */
    public Builder protocolVersion(ProtocolVersion protocolVersion) {
      this.protocolVersion = (protocolVersion != null) ? protocolVersion : DEFAULT_PROTOCOL_VERSION;
      return this;
    }

    /**
     * The Request Template used for the original request.
     *
     * @param requestTemplate used.
     * @return builder reference.
     */
    @Experimental
    public Builder requestTemplate(RequestTemplate requestTemplate) {
      this.requestTemplate = requestTemplate;
      return this;
    }

    public Response build() {
      return new Response(this);
    }
  }

  /**
   * status code. ex {@code 200}
   *
   * <p>See <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html" >rfc2616</a>
   */
  public int status() {
    return status;
  }

  /**
   * Nullable and not set when using http/2 See <a
   * href="https://github.com/http2/http2-spec/issues/202">...</a> See <a
   * href="https://github.com/http2/http2-spec/issues/202">...</a>
   */
  public String reason() {
    return reason;
  }

  /** Returns a case-insensitive mapping of header names to their values. */
  public Map<String, Collection<String>> headers() {
    return headers;
  }

  /** if present, the response had a body */
  public Body body() {
    return body;
  }

  /** the request that generated this response */
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

  /**
   * Returns a charset object based on the requests content type. Defaults to UTF-8 See <a
   * href="https://datatracker.ietf.org/doc/html/rfc7231#section-5.3.3">rfc7231 - Accept-Charset</a>
   * See <a href="https://datatracker.ietf.org/doc/html/rfc7231#section-3.1.1.1">rfc7231 - Media
   * Type</a>
   */
  public Charset charset() {

    Collection<String> contentTypeHeaders = headers().get("Content-Type");

    if (contentTypeHeaders != null) {
      for (String contentTypeHeader : contentTypeHeaders) {
        Charset charset = charsetOf(contentTypeHeader);
        if (charset != null) {
          return charset;
        }
      }
    }

    return Util.UTF_8;
  }

  /**
   * Reads the charset parameter of a single Content-Type header value, in whatever position it
   * appears. Parameters are scanned quote-aware, so a {@code ;} inside a quoted-string parameter
   * value is data rather than a parameter separator (rfc9110 - 5.6.4). Returns null when the header
   * carries no charset parameter, and UTF-8 when it carries an illegal or unsupported one.
   */
  private static Charset charsetOf(String contentTypeHeader) {
    int length = contentTypeHeader.length();
    int start = 0;
    while (start <= length) {
      int end = nextUnquoted(contentTypeHeader, start, length, ';');
      Charset charset = charsetParameterOf(contentTypeHeader, start, end);
      if (charset != null) {
        return charset;
      }
      if (end >= length) {
        return null;
      }
      start = end + 1;
    }
    return null;
  }

  /** Reads {@code name=value} between the given bounds, or null if the name is not charset. */
  private static Charset charsetParameterOf(String contentTypeHeader, int start, int end) {
    int equals = nextUnquoted(contentTypeHeader, start, end, '=');
    if (equals >= end) {
      return null;
    }
    if (!"charset".equalsIgnoreCase(contentTypeHeader.substring(start, equals).trim())) {
      return null;
    }
    try {
      return Charset.forName(unquote(contentTypeHeader.substring(equals + 1, end).trim()));
    } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
      return Util.UTF_8;
    }
  }

  /** Index of the first {@code separator} between the given bounds outside a quoted-string. */
  private static int nextUnquoted(String value, int start, int end, char separator) {
    boolean quoted = false;
    for (int i = start; i < end; i++) {
      char c = value.charAt(i);
      if (quoted) {
        if (c == '\\' && i + 1 < end) {
          i++; // a backslash escapes the next character inside a quoted-string, e.g. \" or \\
        } else if (c == '"') {
          quoted = false;
        }
      } else if (c == '"') {
        quoted = true;
      } else if (c == separator) {
        return i;
      }
    }
    return end;
  }

  /** Removes the surrounding quotes of a quoted-string value and resolves its escapes. */
  private static String unquote(String value) {
    if (value.isEmpty() || value.charAt(0) != '"') {
      return value;
    }
    StringBuilder unquoted = new StringBuilder(value.length());
    for (int i = 1; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '"') {
        return unquoted.toString(); // anything after the closing quote is not part of the value
      }
      if (c == '\\' && i + 1 < value.length()) {
        c = value.charAt(++i);
      }
      unquoted.append(c);
    }
    return unquoted.toString(); // unterminated quoted-string, tolerate it
  }

  @Override
  public String toString() {
    StringBuilder builder =
        new StringBuilder(protocolVersion.toString()).append(" ").append(status);
    if (reason != null) builder.append(' ').append(reason);
    builder.append('\n');
    for (String field : headers.keySet()) {
      for (String value : valuesOrEmpty(headers, field)) {
        builder.append(field).append(": ").append(value).append('\n');
      }
    }
    if (body != null) builder.append('\n').append(body);
    return builder.toString();
  }

  @Override
  public void close() {
    Util.ensureClosed(body);
  }

  public interface Body extends Closeable {

    /**
     * length in bytes, if known. Null if unknown or greater than {@link Integer#MAX_VALUE}. <br>
     * <br>
     * <br>
     * <b>Note</b><br>
     * This is an integer as most implementations cannot do bodies greater than 2GB.
     */
    Integer length();

    /** True if {@link #asInputStream()} and {@link #asReader()} can be called more than once. */
    boolean isRepeatable();

    /** It is the responsibility of the caller to close the stream. */
    InputStream asInputStream() throws IOException;

    /**
     * It is the responsibility of the caller to close the stream.
     *
     * @deprecated favor {@link Body#asReader(Charset)}
     */
    @Deprecated
    default Reader asReader() throws IOException {
      return asReader(StandardCharsets.UTF_8);
    }

    /** It is the responsibility of the caller to close the stream. */
    Reader asReader(Charset charset) throws IOException;
  }

  private static final class InputStreamBody implements Response.Body {

    private final InputStream inputStream;
    private final Integer length;

    private InputStreamBody(InputStream inputStream, Integer length) {
      this.inputStream = inputStream;
      this.length = length;
    }

    private static Body orNull(InputStream inputStream, Integer length) {
      if (inputStream == null) {
        return null;
      }
      return new InputStreamBody(inputStream, length);
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
    public InputStream asInputStream() {
      return inputStream;
    }

    @SuppressWarnings("deprecation")
    @Override
    public Reader asReader() {
      return new InputStreamReader(inputStream, UTF_8);
    }

    @Override
    public Reader asReader(Charset charset) {
      checkNotNull(charset, "charset should not be null");
      return new InputStreamReader(inputStream, charset);
    }

    @Override
    public void close() throws IOException {
      inputStream.close();
    }
  }

  private static final class ByteArrayBody implements Response.Body {

    private final byte[] data;

    public ByteArrayBody(byte[] data) {
      this.data = data;
    }

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

    @Override
    public Integer length() {
      return data.length;
    }

    @Override
    public boolean isRepeatable() {
      return true;
    }

    @Override
    public InputStream asInputStream() {
      return new ByteArrayInputStream(data);
    }

    @SuppressWarnings("deprecation")
    @Override
    public Reader asReader() {
      return new InputStreamReader(asInputStream(), UTF_8);
    }

    @Override
    public Reader asReader(Charset charset) {
      checkNotNull(charset, "charset should not be null");
      return new InputStreamReader(asInputStream(), charset);
    }

    @Override
    public void close() {}
  }
}
