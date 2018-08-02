/**
 * Copyright 2012-2018 The Feign Authors
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

import feign.Request.HttpMethod;
import java.util.Date;

/**
 * This exception is raised when the {@link Response} is deemed to be retryable, typically via an
 * {@link feign.codec.ErrorDecoder} when the {@link Response#status() status} is 503.
 */
public class RetryableException extends FeignException {

  private static final long serialVersionUID = 1L;

  private final Long retryAfter;
  private final HttpMethod httpMethod;

  /**
   * @param retryAfter usually corresponds to the {@link feign.Util#RETRY_AFTER} header.
   */
  public RetryableException(String message, HttpMethod httpMethod, Throwable cause,
      Date retryAfter) {
    super(message, cause);
    this.httpMethod = httpMethod;
    this.retryAfter = retryAfter != null ? retryAfter.getTime() : null;
  }

  /**
   * @param retryAfter usually corresponds to the {@link feign.Util#RETRY_AFTER} header.
   */
  public RetryableException(String message, HttpMethod httpMethod, Date retryAfter) {
    super(message);
    this.httpMethod = httpMethod;
    this.retryAfter = retryAfter != null ? retryAfter.getTime() : null;
  }

  /**
   * Sometimes corresponds to the {@link feign.Util#RETRY_AFTER} header present in {@code 503}
   * status. Other times parsed from an application-specific response. Null if unknown.
   */
  public Date retryAfter() {
    return retryAfter != null ? new Date(retryAfter) : null;
  }

  public HttpMethod method() {
    return this.httpMethod;
  }
}
