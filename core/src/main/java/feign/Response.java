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

import static feign.Util.caseInsensitiveCopyOf;
import static feign.Util.checkNotNull;
import static feign.Util.checkState;
import static feign.Util.valuesOrEmpty;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import feign.HttpBodyFactory.HttpBody;
import feign.Request.ProtocolVersion;

/** An immutable response to an http invocation which only returns string content. */
public final class Response implements Closeable {

  private final int status;
  private final String reason;
  private final Map<String, Collection<String>> headers;
  private final HttpBody body;
  private final Request request;
  private final ProtocolVersion protocolVersion;

  private Response(Builder builder) {
    checkState(builder.request != null, "original request is required");
    this.status = builder.status;
    this.request = builder.request;
    this.reason = builder.reason; // nullable
    this.headers = caseInsensitiveCopyOf(builder.headers);
    this.body = builder.bodySupplier.apply(builder); // nullable
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
    int status;
    String reason;
    Map<String, Collection<String>> headers = Collections.emptyMap();
    Function<Builder, HttpBody> bodySupplier = b -> null;
    Request request;
    private RequestTemplate requestTemplate;
    private ProtocolVersion protocolVersion = DEFAULT_PROTOCOL_VERSION;

    Builder() {}

    Builder(Response source) {
      this.status = source.status;
      this.reason = source.reason;
      this.headers = source.headers;
      this.bodySupplier = b -> source.body;
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
    // TODO: KD - is there some way to make body so it can't be null?  Seems like an empty body would be much better
    public Builder body(HttpBody body) {
      this.bodySupplier = b -> body;
      return this;
    }

    /**
     * @see Response#body
     */
    // TODO: KD - is there some way to make body so it can't be null?  Seems like an empty body would be much better
    public Builder body(InputStream inputStream, long length) {
      this.bodySupplier = b -> inputStream == null ? null : HttpBodyFactory.forInputStream(inputStream, length, charsetFromHeaders(b.headers));
      return this;
    }

    /**
     * @see Response#body
     */
    // TODO: KD - is there some way to make body so it can't be null?  Seems like an empty body would be much better
    public Builder body(byte[] data) {
      this.bodySupplier = b -> data == null ? null : HttpBodyFactory.forBytes(data, charsetFromHeaders(b.headers));
      return this;
    }

    /**
     * @see Response#body
     */
    //TODO: KD - this is weird.  So we are specifying a text string in a charset that is potentially different than the charset of the HTTP response?
    // TODO: KD - is there some way to make body so it can't be null?  Seems like an empty body would be much better
    public Builder body(String text, Charset charset) {
      return body(text.getBytes(charset));
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
  public HttpBody body() {
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
    return charsetFromHeaders(headers());
  }

  // TODO: KD - should this be in a utility class?
  public static Charset charsetFromHeaders(Map<String, Collection<String>> headers) {
	  
	  return getContentTypeHeader(headers)
			  	.flatMap(h -> getEncodingFromContentTypeHeader(h))
			  	.orElse(Util.UTF_8);
	  
  }
  
  public static Optional<String> getContentTypeHeader(Map<String, Collection<String>> headers) {
	    Collection<String> contentTypeHeaders = headers.get(Util.CONTENT_TYPE);
	    if (contentTypeHeaders == null || contentTypeHeaders.isEmpty()) return Optional.empty();

	    return Optional.of(contentTypeHeaders.iterator().next());
  }
  
  public static Optional<Charset> getEncodingFromContentTypeHeader(String contentTypeHeader) {
      String[] contentTypeParmeters = contentTypeHeader.split(";");
      if (contentTypeParmeters.length > 1) {
        String[] charsetParts = contentTypeParmeters[1].split("=");
        if (charsetParts.length == 2 && "charset".equalsIgnoreCase(charsetParts[0].trim())) {
          String charsetString = charsetParts[1].replaceAll("\"", "");
          // TODO: KD - should we catch IllegalCharsetNameException and return UTF_8 ?
          return Optional.of( Charset.forName(charsetString) );
        }
      }
	  
      return Optional.empty();
  }
  
  public static String getContentTypeFromContentTypeHeader(String contentTypeHeader) {
      String[] contentTypeParmeters = contentTypeHeader.split(";");
      return contentTypeParmeters[0];
  }
  
  public static String addEncodingToContentTypeHeader(String contentTypeHeader, Charset encoding) {

	  String contentType = getContentTypeFromContentTypeHeader(contentTypeHeader);

	  return Optional.ofNullable(encoding)
			  .filter(e -> !Util.UTF_8.equals(e)) // don't include charset=UTF-8 (the default)
			  .map(e -> contentType + "; charset=" + e.name())
			  .orElse(contentType);
	  
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

  /**
   * @deprecated use HttpBody instead
   */
  // TODO: KD - we should be able to remove this interface
  @Deprecated
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


}
