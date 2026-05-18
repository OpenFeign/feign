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

import static feign.Util.checkNotNull;
import static feign.Util.getThreadIdentifier;
import static feign.Util.valuesOrEmpty;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** An immutable request to an http server. */
public final class Request {

  public enum HttpMethod {
    GET,
    HEAD,
    POST(true),
    PUT(true),
    DELETE,
    CONNECT,
    OPTIONS,
    TRACE,
    PATCH(true);

    private final boolean withBody;

    HttpMethod() {
      this(false);
    }

    HttpMethod(boolean withBody) {
      this.withBody = withBody;
    }

    public boolean isWithBody() {
      return this.withBody;
    }
  }

  public enum ProtocolVersion {
    HTTP_1_0("HTTP/1.0"),
    HTTP_1_1("HTTP/1.1"),
    HTTP_2("HTTP/2.0"),
    MOCK;

    final String protocolVersion;

    ProtocolVersion() {
      protocolVersion = name();
    }

    ProtocolVersion(String protocolVersion) {
      this.protocolVersion = protocolVersion;
    }

    @Override
    public String toString() {
      return protocolVersion;
    }
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
  private final ProtocolVersion protocolVersion;

  /**
   * Creates a new Request.
   *
   * @param method of the request.
   * @param url for the request.
   * @param headers for the request.
   * @param body for the request, optional.
   * @param requestTemplate used to build the request.
   */
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
    protocolVersion = ProtocolVersion.HTTP_1_1;
  }

  /**
   * Http Method for the request.
   *
   * @return the HttpMethod.
   */
  public HttpMethod httpMethod() {
    return this.httpMethod;
  }

  /**
   * URL for the request.
   *
   * @return URL as a String.
   */
  public String url() {
    return url;
  }

  /**
   * Request Headers.
   *
   * @return the request headers.
   */
  public Map<String, Collection<String>> headers() {
    return Collections.unmodifiableMap(headers);
  }

  /**
   * Add new entries to request Headers. It overrides existing entries
   *
   * @param key
   * @param value
   */
  public void header(String key, String value) {
    header(key, Arrays.asList(value));
  }

  /**
   * Add new entries to request Headers. It overrides existing entries
   *
   * @param key
   * @param values
   */
  public void header(String key, Collection<String> values) {
    headers.put(key, values);
  }

  /**
   * Returns the body of the request, if any.
   *
   * @return the body of the request, if any
   */
  public Optional<Body> body() {
    return Optional.ofNullable(body);
  }

  /**
   * Request HTTP protocol version
   *
   * @return HTTP protocol version
   */
  public ProtocolVersion protocolVersion() {
    return protocolVersion;
  }

  /**
   * Request as an HTTP/1.1 request.
   *
   * @return the request.
   */
  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder
        .append(httpMethod)
        .append(' ')
        .append(url)
        .append(' ')
        .append(protocolVersion)
        .append('\n');
    for (final String field : headers.keySet()) {
      for (final String value : valuesOrEmpty(headers, field)) {
        builder.append(field).append(": ").append(value).append('\n');
      }
    }
    if (body != null) {
      builder.append('\n').append(body);
    }
    return builder.toString();
  }

  /**
   * Controls the per-request settings currently required to be implemented by all {@link Client
   * clients}
   */
  public static class Options {

    private final long connectTimeout;
    private final TimeUnit connectTimeoutUnit;
    private final long readTimeout;
    private final TimeUnit readTimeoutUnit;
    private final boolean followRedirects;
    private final Map<String, Map<String, Options>> threadToMethodOptions;

    /**
     * Get an Options by methodName
     *
     * @param methodName it's your FeignInterface method name.
     * @return method Options
     */
    @Experimental
    public Options getMethodOptions(String methodName) {
      Map<String, Options> methodOptions =
          threadToMethodOptions.getOrDefault(getThreadIdentifier(), new HashMap<>());
      return methodOptions.getOrDefault(methodName, this);
    }

    /**
     * Set methodOptions by methodKey and options
     *
     * @param methodName it's your FeignInterface method name.
     * @param options it's the Options for this method.
     */
    @Experimental
    public void setMethodOptions(String methodName, Options options) {
      String threadIdentifier = getThreadIdentifier();
      Map<String, Request.Options> methodOptions =
          threadToMethodOptions.getOrDefault(threadIdentifier, new HashMap<>());
      threadToMethodOptions.put(threadIdentifier, methodOptions);
      methodOptions.put(methodName, options);
    }

    /**
     * Creates a new Options instance.
     *
     * @param connectTimeoutMillis connection timeout in milliseconds.
     * @param readTimeoutMillis read timeout in milliseconds.
     * @param followRedirects if the request should follow 3xx redirections.
     * @deprecated please use {@link #Options(long, TimeUnit, long, TimeUnit, boolean)}
     */
    @Deprecated
    public Options(int connectTimeoutMillis, int readTimeoutMillis, boolean followRedirects) {
      this(
          connectTimeoutMillis,
          TimeUnit.MILLISECONDS,
          readTimeoutMillis,
          TimeUnit.MILLISECONDS,
          followRedirects);
    }

    /**
     * Creates a new Options Instance.
     *
     * @param connectTimeout value.
     * @param connectTimeoutUnit with the TimeUnit for the timeout value.
     * @param readTimeout value.
     * @param readTimeoutUnit with the TimeUnit for the timeout value.
     * @param followRedirects if the request should follow 3xx redirections.
     */
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
      this.threadToMethodOptions = new ConcurrentHashMap<>();
    }

    /**
     * Creates a new Options instance that follows redirects by default.
     *
     * @param connectTimeoutMillis connection timeout in milliseconds.
     * @param readTimeoutMillis read timeout in milliseconds.
     * @deprecated please use {@link #Options(long, TimeUnit, long, TimeUnit, boolean)}
     */
    @Deprecated
    public Options(int connectTimeoutMillis, int readTimeoutMillis) {
      this(connectTimeoutMillis, readTimeoutMillis, true);
    }

    /**
     * Creates a new Options Instance.
     *
     * @param connectTimeout value.
     * @param readTimeout value.
     * @param followRedirects if the request should follow 3xx redirections.
     */
    public Options(Duration connectTimeout, Duration readTimeout, boolean followRedirects) {
      this(
          connectTimeout.toMillis(),
          TimeUnit.MILLISECONDS,
          readTimeout.toMillis(),
          TimeUnit.MILLISECONDS,
          followRedirects);
    }

    /**
     * Creates the new Options instance using the following defaults:
     *
     * <ul>
     *   <li>Connect Timeout: 10 seconds
     *   <li>Read Timeout: 60 seconds
     *   <li>Follow all 3xx redirects
     * </ul>
     */
    public Options() {
      this(10, TimeUnit.SECONDS, 60, TimeUnit.SECONDS, true);
    }

    /**
     * Defaults to 10 seconds. {@code 0} implies no timeout.
     *
     * @see java.net.HttpURLConnection#getConnectTimeout()
     */
    public int connectTimeoutMillis() {
      return (int) connectTimeoutUnit.toMillis(connectTimeout);
    }

    /**
     * Defaults to 60 seconds. {@code 0} implies no timeout.
     *
     * @see java.net.HttpURLConnection#getReadTimeout()
     */
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

    /**
     * Connect Timeout Value.
     *
     * @return current timeout value.
     */
    public long connectTimeout() {
      return connectTimeout;
    }

    /**
     * TimeUnit for the Connection Timeout value.
     *
     * @return TimeUnit
     */
    public TimeUnit connectTimeoutUnit() {
      return connectTimeoutUnit;
    }

    /**
     * Read Timeout value.
     *
     * @return current read timeout value.
     */
    public long readTimeout() {
      return readTimeout;
    }

    /**
     * TimeUnit for the Read Timeout value.
     *
     * @return TimeUnit
     */
    public TimeUnit readTimeoutUnit() {
      return readTimeoutUnit;
    }
  }

  @Experimental
  public RequestTemplate requestTemplate() {
    return this.requestTemplate;
  }

  /**
   * Request Body
   *
   * <p>Considered experimental, will most likely be made internal going forward.
   */
  @Experimental
  public interface Body {
    /**
     * Creates a new {@link Body} instance from the provided string content. It's assumed that the
     * content was constructed using {@link StandardCharsets#UTF_8} charset.
     *
     * @param content the string content to be used as the body of the request
     * @return a new {@link Body} instance containing the provided string content
     */
    static Body of(String content) {
      return of(content, StandardCharsets.UTF_8);
    }

    /**
     * Creates a new {@link Body} instance from the provided byte array. It's assumed that the byte
     * array can be converted to a string using {@link StandardCharsets#UTF_8} charset.
     *
     * @param content the byte array representing the body content
     * @return a new {@link Body} instance
     */
    static Body of(byte[] content) {
      return of(content, StandardCharsets.UTF_8);
    }

    /**
     * Creates a new {@link Body} instance from the provided string content, using the specified
     * charset.
     *
     * @param content the string content to be used as the body content
     * @param charset the content charset
     * @return a new {@link Body} instance containing the provided content
     */
    static Body of(String content, Charset charset) {
      Objects.requireNonNull(content, "content is required");
      Objects.requireNonNull(charset, "charset is required");

      return of(content.getBytes(charset), charset);
    }

    /**
     * Creates a new {@link Body} instance from the provided byte array, using the specified
     * charset.
     *
     * @param content the byte array representing the body content
     * @param charset the content charset
     * @return a new {@link Body} instance
     */
    static Body of(byte[] content, Charset charset) {
      return new Request.BodyImpl(content, charset);
    }

    /**
     * Writes the body content to the provided {@link OutputStream}.
     *
     * @param outputStream the output stream to which the body content should be written
     * @throws IOException if an I/O error occurs while writing the body content
     */
    void writeTo(OutputStream outputStream) throws IOException;

    /**
     * Writes the body content to a string using the specified charset for decoding.
     *
     * @param charset the charset to be used for decoding the body content
     * @return a string representation of the body content
     * @throws IOException if an I/O error occurs while writing the body content to a string
     */
    default String writeToString(Charset charset) throws IOException {
      Objects.requireNonNull(charset, "charset is required");
      return new String(writeToByteArray(), charset);
    }

    /**
     * Writes the body content to a byte array.
     *
     * @return a byte array containing the body content
     * @throws IOException if an I/O error occurs while writing the body content to a byte array
     */
    default byte[] writeToByteArray() throws IOException {
      try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
        writeTo(outputStream);
        return outputStream.toByteArray();
      }
    }

    /**
     * Indicates whether the body can be written multiple times. This is important for clients that
     * may need to retry requests, as non-repeatable bodies (e.g., streaming data) cannot be
     * re-sent.
     *
     * @return {@code true} if the body can be written multiple times, {@code false} otherwise
     */
    boolean isRepeatable();

    /**
     * Returns the content length of the body, or {@code -1} if unknown. This can be used by clients
     * to set the {@code Content-Length} header.
     *
     * @return the content length, or {@code -1} if unknown
     */
    long contentLength();
  }

  private static class BodyImpl implements Body {
    private final byte[] content;
    private final Charset charset;

    private BodyImpl(byte[] content, Charset charset) {
      this.content = Objects.requireNonNull(content, "content must not be null");
      this.charset = Objects.requireNonNull(charset, "charset must not be null");
    }

    @Override
    public void writeTo(OutputStream outputStream) throws IOException {
      Objects.requireNonNull(outputStream, "outputStream is required").write(content);
    }

    @Override
    public boolean isRepeatable() {
      return true;
    }

    @Override
    public long contentLength() {
      return content.length;
    }

    @Override
    public String toString() {
      return new String(content, charset);
    }
  }
}
