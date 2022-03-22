/*
 * Copyright 2012-2022 The Feign Authors
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
package feign.slf4j;

import feign.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import static feign.Util.*;
import static java.util.Arrays.asList;
import static java.util.Objects.nonNull;

/**
 * <a href="https://www.slf4j.org/">SLF4J</a> logging for debug messages.
 * <p>
 * Log the request method and URL and the response status code and execution time. See
 * {@link Builder} for other options.
 *
 * @since 11.9
 */
public class Slf4jCapability implements Capability {

  private final Logger logger;
  private final List<String> allowedHeaders;
  private final List<String> deniedHeaders;
  private final boolean withHeaders;
  private final boolean withRequest;
  private final boolean withResponse;
  private final boolean withRetryer;
  private final boolean withStacktrace;

  private Slf4jCapability(Builder builder) {
    logger = builder.logger;
    allowedHeaders = asList(builder.allowedHeaders);
    deniedHeaders = asList(builder.deniedHeaders);
    withHeaders = builder.withHeaders;
    withRequest = builder.withRequest;
    withResponse = builder.withResponse;
    withRetryer = builder.withRetryer;
    withStacktrace = builder.withStacktrace;

  }

  public static Builder builder() {
    return new Builder();
  }

  static String methodTag(String configKey) {
    return '[' + configKey.substring(0, configKey.indexOf('(')) + "] ";
  }

  static String resolveProtocolVersion(Request.ProtocolVersion protocolVersion) {
    if (nonNull(protocolVersion)) {
      return protocolVersion.toString();
    }
    return "UNKNOWN";
  }

  @Override
  public Client enrich(Client client) {
    return new LoggedClient(client, logger, allowedHeaders, deniedHeaders, withHeaders, withRequest,
        withResponse, withStacktrace);
  }

  @Override
  public Retryer enrich(Retryer retryer) {
    return (withRetryer) ? new LoggedRetryer(retryer, logger) : retryer;
  }


  /**
   * Logger capability builder.
   * <p>
   * All options are isolated: you can use {@link #withResponse()}} to print a response body without
   * headers unlike {@link feign.Logger.Level#FULL} does.
   * <p>
   * There is nothing like {@link feign.Logger.Level#NONE}: if you don't set any option it works
   * like {@link feign.Logger.Level#BASIC}.
   * <p>
   * Both {@link #withAllowedHeaders(String...)} and {@link #withDeniedHeaders(String...)} set the
   * flag <code>withHeaders</code>. They are case-insensitive.
   * <ol>
   * <li>if <code>allowed headers<</code> is not empty it writes out only allowed headers;</li>
   * <li>if <code>allowed headers</code> is empty and <code>denied headers</code> isn't it writes
   * out all headers other than denies ones;</li>
   * <li>if both lists are empty it writes out all headers.</li>
   * </ol>
   */
  public static class Builder {

    public static final String FEIGN_LOGGER = "feign.logger";

    private Logger logger;
    private String[] allowedHeaders = {};
    private String[] deniedHeaders = {};
    private boolean withHeaders;
    private boolean withRequest;
    private boolean withResponse;
    private boolean withRetryer;
    private boolean withStacktrace;

    private Builder() {
      logger = LoggerFactory.getLogger(FEIGN_LOGGER);
    }

    /**
     * Set a custom logger. The default one uses the name <em>feign.logger</em>.
     *
     * @param logger logger
     * @return the builder
     */
    public Builder logger(Logger logger) {
      this.logger = Objects.requireNonNull(logger, "Logger must not be null.");
      return this;
    }

    /**
     * Log allowed request and response headers.
     *
     * @param allowedHeaders allowed headers
     * @return the builder
     */
    public Builder withAllowedHeaders(String... allowedHeaders) {
      this.allowedHeaders = allowedHeaders;
      return withHeaders();
    }

    /**
     * Log request and response headers other than denied ones.
     *
     * @param deniedHeaders denied headers
     * @return the builder
     */
    public Builder withDeniedHeaders(String... deniedHeaders) {
      this.deniedHeaders = deniedHeaders;
      return withHeaders();
    }

    /**
     * Log request and response headers.
     *
     * @return the builder
     */
    public Builder withHeaders() {
      this.withHeaders = true;
      return this;
    }

    /**
     * Log the request body and metadata.
     *
     * @return the builder
     */
    public Builder withRequest() {
      this.withRequest = true;
      return this;
    }

    /**
     * Log the response body and metadata.
     *
     * @return the builder
     */
    public Builder withResponse() {
      this.withResponse = true;
      return this;
    }

    /**
     * Log when Feign retries to call.
     *
     * @return the builder
     */
    public Builder withRetryer() {
      this.withRetryer = true;
      return this;
    }

    /**
     * Log full stacktrace, usually for {@link java.io.IOException}.
     *
     * @return the builder
     */
    public Builder withStacktrace() {
      this.withStacktrace = true;
      return this;
    }

    public Slf4jCapability build() {
      return new Slf4jCapability(this);
    }

  }


  /**
   * Logged {@link Client}.
   */
  static class LoggedClient implements Client {

    private static final String EMPTY = "";

    private final Client delegate;
    private final Logger logger;
    private final List<String> allowedHeaders;
    private final List<String> deniedHeaders;
    private final boolean withHeaders;
    private final boolean withRequest;
    private final boolean withResponse;
    private final boolean withStacktrace;

    LoggedClient(Client delegate,
        Logger logger,
        List<String> allowedHeaders,
        List<String> deniedHeaders,
        boolean withHeaders,
        boolean withRequest,
        boolean withResponse,
        boolean withStacktrace) {
      this.delegate = delegate;
      this.logger = logger;
      this.allowedHeaders = allowedHeaders;
      this.deniedHeaders = deniedHeaders;
      this.withHeaders = withHeaders;
      this.withRequest = withRequest;
      this.withResponse = withResponse;
      this.withStacktrace = withStacktrace;
    }

    /**
     * Write to log request method and URL, response status code, execution time, headers, request
     * and response bodies, IOException's stacktrace.
     *
     * @param request safe to replay.
     * @param options options to apply to this request.
     * @return connected response, {@link Response.Body} is optionally rebuffered if a response is
     *         logged.
     * @throws IOException on a network error connecting to {@link Request#url()}.
     * @see Builder
     */
    @Override
    public Response execute(Request request, Request.Options options) throws IOException {
      String methodTag = methodTag(request.requestTemplate().methodMetadata().configKey());
      String protocolVersion = resolveProtocolVersion(request.protocolVersion());

      logRequest(methodTag, protocolVersion, request);

      long start = System.nanoTime();

      try {
        Response response = delegate.execute(request, options);

        return logAndRebufferResponse(methodTag, protocolVersion, response, elapsedTime(start));
      } catch (IOException exception) {
        throw logIOException(methodTag, exception, elapsedTime(start));
      }
    }

    // visible for testing
    long elapsedTime(long start) {
      return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private void logHeaders(String methodTag, Map<String, Collection<String>> headers) {
      if (withHeaders) {
        for (String header : headers.keySet()) {
          if (shouldLogHeader(header)) {
            for (String value : valuesOrEmpty(headers, header)) {
              logger.debug(methodTag + "{}: {}", header, value);
            }
          }
        }
      }
    }

    private IOException logIOException(String methodTag, IOException exception, long elapsedTime) {
      if (logger.isDebugEnabled()) {
        logger.debug(methodTag + "<--- ERROR {}: {} ({}ms)", exception.getClass().getSimpleName(),
            exception.getMessage(), elapsedTime);
        if (withStacktrace) {
          StringWriter writer = new StringWriter();
          exception.printStackTrace(new PrintWriter(writer));
          logger.debug(methodTag + "{}", writer);
          logger.debug(methodTag + "<--- END ERROR");
        }
      }

      return exception;
    }

    private Response logAndRebufferResponse(String methodTag,
                                            String protocolVersion,
                                            Response response,
                                            long elapsedTime)
        throws IOException {
      if (logger.isDebugEnabled()) {
        String reason = (nonNull(response.reason())) ? response.reason() : EMPTY;
        int status = response.status();

        logger.debug(methodTag + "<--- {} {} {} ({}ms)", protocolVersion, status, reason,
            elapsedTime);
        logHeaders(methodTag, response.headers());

        int bodyLength = 0;

        if (nonNull(response.body()) && !(status == 204 || status == 205)) {
          byte[] bodyData = Util.toByteArray(response.body().asInputStream());

          bodyLength = bodyData.length;
          if (withResponse && bodyLength > 0) {
            logger.debug(methodTag);
            logger.debug(methodTag + "{}", decodeOrDefault(bodyData, UTF_8, "Binary data"));
            logger.debug(methodTag + "<--- END HTTP ({}-byte body)", bodyLength);
            return response.toBuilder().body(bodyData).build();
          }
        }
        logger.debug(methodTag + "<--- END HTTP ({}-byte body)", bodyLength);
      }

      return response;
    }

    private void logRequest(String methodTag, String protocolVersion, Request request) {
      if (logger.isDebugEnabled()) {
        logger.debug(methodTag + "---> {} {} {}", request.httpMethod().name(), request.url(),
            protocolVersion);
        logHeaders(methodTag, request.headers());

        int bodyLength = 0;

        if (nonNull(request.body())) {
          bodyLength = request.length();
          if (withRequest) {
            String bodyText =
                (nonNull(request.charset())) ? new String(request.body(), request.charset()) : null;
            logger.debug(methodTag); // empty line
            logger.debug(methodTag + "{}", (nonNull(bodyText) ? bodyText : "Binary data"));
          }
        }
        logger.debug(methodTag + "---> END HTTP ({}-byte body)", bodyLength);
      }
    }

    private boolean shouldLogHeader(String header) {
      if (!allowedHeaders.isEmpty()) {
        return allowedHeaders.stream().anyMatch(header::equalsIgnoreCase);
      }
      if (!deniedHeaders.isEmpty()) {
        return deniedHeaders.stream().noneMatch(header::equalsIgnoreCase);
      }

      return true;
    }

  }


  /**
   * Logged {@link Retryer}.
   */
  static class LoggedRetryer implements Retryer {

    private final Retryer delegate;
    private final Logger logger;

    LoggedRetryer(Retryer delegate, Logger logger) {
      this.delegate = delegate;
      this.logger = logger;
    }

    /**
     * If retry is permitted, write to log (on <strong>DEBUG</strong> level) then return. Otherwise,
     * propagate the exception.
     *
     * @param retryableException retryable exception
     * @see Builder
     */
    @Override
    public void continueOrPropagate(RetryableException retryableException) {
      delegate.continueOrPropagate(retryableException);
      if (logger.isDebugEnabled()) {
        logger.debug(
            methodTag(retryableException.request().requestTemplate().methodMetadata().configKey())
                + "---> RETRYING");
      }
    }

    @Override
    public Retryer clone() {
      return new LoggedRetryer(delegate, logger);
    }

  }

}
