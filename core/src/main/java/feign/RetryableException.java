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

import feign.Request.HttpMethod;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * This exception is raised when the {@link Response} is deemed to be retryable, typically via an
 * {@link feign.codec.ErrorDecoder} when the {@link Response#status() status} is 503.
 */
public class RetryableException extends FeignException {

  private static final long serialVersionUID = 3L;

  private final Long retryAfter;
  private final HttpMethod httpMethod;
  private final String methodKey;

  /**
   * Represents a non-retryable exception when Retry-After information is explicitly not provided.
   *
   * <p>Use this constructor when the server response does not include a Retry-After header or when
   * retries are not expected.
   *
   * @param status the HTTP status code
   * @param message the exception message
   * @param httpMethod the HTTP method (GET, POST, etc.)
   * @param request the original HTTP request
   */
  public RetryableException(int status, String message, HttpMethod httpMethod, Request request) {
    super(status, message, request);
    this.httpMethod = httpMethod;
    this.retryAfter = null;
    this.methodKey = null;
  }

  /**
   * Represents a non-retryable exception when Retry-After information is explicitly not provided.
   *
   * <p>Use this constructor when the server response does not include a Retry-After header or when
   * retries are not expected.
   *
   * @param status the HTTP status code
   * @param message the exception message
   * @param httpMethod the HTTP method (GET, POST, etc.)
   * @param cause the underlying cause of the exception
   * @param request the original HTTP request
   */
  public RetryableException(
      int status, String message, HttpMethod httpMethod, Throwable cause, Request request) {
    super(status, message, request, cause);
    this.httpMethod = httpMethod;
    this.retryAfter = null;
    this.methodKey = null;
  }

  /**
   * Represents a retryable exception when Retry-After information is available.
   *
   * <p>Use this constructor when the server response includes a Retry-After header specifying the
   * delay in milliseconds before retrying.
   *
   * <p>If {@code retryAfter} is {@code null}, prefer using {@link #RetryableException(int, String,
   * HttpMethod, Throwable, Request)} instead.
   *
   * @param status the HTTP status code
   * @param message the exception message
   * @param httpMethod the HTTP method (GET, POST, etc.)
   * @param cause the underlying cause of the exception
   * @param retryAfter the retry delay in milliseconds retryAfter usually corresponds to the {@link
   *     feign.Util#RETRY_AFTER} header. If you don't want to retry, use {@link
   *     #RetryableException(int, String, HttpMethod, Throwable, Request)}.
   * @param request the original HTTP request
   */
  public RetryableException(
      int status,
      String message,
      HttpMethod httpMethod,
      Throwable cause,
      Long retryAfter,
      Request request) {
    super(status, message, request, cause);
    this.httpMethod = httpMethod;
    this.retryAfter = retryAfter;
    this.methodKey = null;
  }

  /**
   * Represents a retryable exception with methodKey for identifying the method being retried.
   *
   * @param status the HTTP status code
   * @param message the exception message
   * @param httpMethod the HTTP method (GET, POST, etc.)
   * @param cause the underlying cause of the exception
   * @param retryAfter the retry delay in milliseconds
   * @param request the original HTTP request
   * @param methodKey the method key identifying the Feign method
   */
  public RetryableException(
      int status,
      String message,
      HttpMethod httpMethod,
      Throwable cause,
      Long retryAfter,
      Request request,
      String methodKey) {
    super(status, message, request, cause);
    this.httpMethod = httpMethod;
    this.retryAfter = retryAfter;
    this.methodKey = methodKey;
  }

  /**
   * @deprecated Use {@link #RetryableException(int, String, HttpMethod, Throwable, Long, Request)}
   *     instead. This constructor uses {@link Date} for retryAfter, which has been replaced by
   *     {@link Long}.
   * @param status the HTTP status code
   * @param message the exception message
   * @param httpMethod the HTTP method (GET, POST, etc.)
   * @param cause the underlying cause of the exception
   * @param retryAfter the retry-after time as a {@link Date}
   * @param request the original HTTP request
   */
  @Deprecated
  public RetryableException(
      int status,
      String message,
      HttpMethod httpMethod,
      Throwable cause,
      Date retryAfter,
      Request request) {
    super(status, message, request, cause);
    this.httpMethod = httpMethod;
    this.retryAfter = retryAfter != null ? retryAfter.getTime() : null;
    this.methodKey = null;
  }

  /**
   * Represents a retryable exception when Retry-After information is available.
   *
   * <p>Use this constructor when the server response includes a Retry-After header.
   *
   * @param status the HTTP status code
   * @param message the exception message
   * @param httpMethod the HTTP method (GET, POST, etc.)
   * @param retryAfter the retry delay in milliseconds retryAfter usually corresponds to the {@link
   *     feign.Util#RETRY_AFTER} header. If you don't want to retry, use {@link
   *     #RetryableException(int, String, HttpMethod, Request)}
   * @param request the original HTTP request
   */
  public RetryableException(
      int status, String message, HttpMethod httpMethod, Long retryAfter, Request request) {
    super(status, message, request);
    this.httpMethod = httpMethod;
    this.retryAfter = retryAfter;
    this.methodKey = null;
  }

  /**
   * @deprecated Use {@link #RetryableException(int, String, HttpMethod, Long, Request)} instead.
   *     This constructor uses {@link Date} for retryAfter, which has been replaced by {@link Long}.
   * @param status the HTTP status code
   * @param message the exception message
   * @param httpMethod the HTTP method (GET, POST, etc.)
   * @param retryAfter the retry-after time as a {@link Date}
   * @param request the original HTTP request
   */
  @Deprecated
  public RetryableException(
      int status, String message, HttpMethod httpMethod, Date retryAfter, Request request) {
    super(status, message, request);
    this.httpMethod = httpMethod;
    this.retryAfter = retryAfter != null ? retryAfter.getTime() : null;
    this.methodKey = null;
  }

  /**
   * Represents a retryable exception with response body and headers.
   *
   * <p>Use this constructor when handling HTTP responses that include Retry-After information.
   *
   * @param status the HTTP status code
   * @param message the exception message
   * @param httpMethod the HTTP method (GET, POST, etc.)
   * @param retryAfter the retry delay in milliseconds retryAfter usually corresponds to the {@link
   *     feign.Util#RETRY_AFTER} header.
   * @param request the original HTTP request
   * @param responseBody the HTTP response body
   * @param responseHeaders the HTTP response headers
   */
  public RetryableException(
      int status,
      String message,
      HttpMethod httpMethod,
      Long retryAfter,
      Request request,
      byte[] responseBody,
      Map<String, Collection<String>> responseHeaders) {
    super(status, message, request, responseBody, responseHeaders);
    this.httpMethod = httpMethod;
    this.retryAfter = retryAfter;
    this.methodKey = null;
  }

  /**
   * @deprecated Use {@link #RetryableException(int, String, HttpMethod, Long, Request, byte[],
   *     Map)} instead. This constructor uses {@link Date} for retryAfter, which has been replaced
   *     by {@link Long}.
   * @param status the HTTP status code
   * @param message the exception message
   * @param httpMethod the HTTP method (GET, POST, etc.)
   * @param retryAfter the retry-after time as a {@link Date}
   * @param request the original HTTP request
   * @param responseBody the HTTP response body
   * @param responseHeaders the HTTP response headers
   */
  @Deprecated
  public RetryableException(
      int status,
      String message,
      HttpMethod httpMethod,
      Date retryAfter,
      Request request,
      byte[] responseBody,
      Map<String, Collection<String>> responseHeaders) {
    super(status, message, request, responseBody, responseHeaders);
    this.httpMethod = httpMethod;
    this.retryAfter = retryAfter != null ? retryAfter.getTime() : null;
    this.methodKey = null;
  }

  /**
   * Sometimes corresponds to the {@link feign.Util#RETRY_AFTER} header present in {@code 503}
   * status. Other times parsed from an application-specific response. Null if unknown.
   */
  public Long retryAfter() {
    return retryAfter;
  }

  public HttpMethod method() {
    return this.httpMethod;
  }

  /**
   * Returns the method key identifying the Feign method that was being invoked.
   * This corresponds to the methodKey parameter in {@link feign.codec.ErrorDecoder#decode}.
   *
   * @return the method key, or null if not set
   */
  public String methodKey() {
    return this.methodKey;
  }
}
