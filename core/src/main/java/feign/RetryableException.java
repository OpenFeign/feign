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

import java.util.Date;

/**
 * This exception is raised when the {@link Response} is deemed to be retryable, typically via an
 * {@link feign.codec.ErrorDecoder} when the {@link Response#status() status} is 503.
 */
public class RetryableException extends FeignException {

  private static final long serialVersionUID = 1L;

  private final Long retryAfter;

  /**
   * @param retryAfter usually corresponds to the {@link feign.Util#RETRY_AFTER} header.
   */
  public RetryableException(String message, Throwable cause, Date retryAfter) {
    super(message, cause);
    this.retryAfter = retryAfter != null ? retryAfter.getTime() : null;
  }

  /**
   * @param retryAfter usually corresponds to the {@link feign.Util#RETRY_AFTER} header.
   */
  public RetryableException(String message, Date retryAfter) {
    super(message);
    this.retryAfter = retryAfter != null ? retryAfter.getTime() : null;
  }

  /**
   * Sometimes corresponds to the {@link feign.Util#RETRY_AFTER} header present in {@code 503}
   * status. Other times parsed from an application-specific response.  Null if unknown.
   */
  public Date retryAfter() {
    return retryAfter != null ? new Date(retryAfter) : null;
  }
}
