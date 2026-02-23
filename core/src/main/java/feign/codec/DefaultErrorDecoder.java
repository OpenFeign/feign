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
package feign.codec;

import static feign.FeignException.errorStatus;
import static feign.Util.RETRY_AFTER;

import feign.FeignException;
import feign.Response;
import feign.RetryableException;
import java.util.Collection;
import java.util.Map;

public class DefaultErrorDecoder implements ErrorDecoder {

  private final ErrorDecoder.RetryAfterDecoder retryAfterDecoder =
      new ErrorDecoder.RetryAfterDecoder();
  private Integer maxBodyBytesLength;
  private Integer maxBodyCharsLength;

  public DefaultErrorDecoder() {
    this.maxBodyBytesLength = null;
    this.maxBodyCharsLength = null;
  }

  public DefaultErrorDecoder(Integer maxBodyBytesLength, Integer maxBodyCharsLength) {
    this.maxBodyBytesLength = maxBodyBytesLength;
    this.maxBodyCharsLength = maxBodyCharsLength;
  }

  @Override
  public Exception decode(String methodKey, Response response) {
    FeignException exception =
        errorStatus(methodKey, response, maxBodyBytesLength, maxBodyCharsLength);
    Long retryAfter = retryAfterDecoder.apply(firstOrNull(response.headers(), RETRY_AFTER));
    if (retryAfter != null) {
      return new RetryableException(
          response.status(),
          exception.getMessage(),
          response.request().httpMethod(),
          exception,
          retryAfter,
          response.request(),
          methodKey);
    }
    return exception;
  }

  private <T> T firstOrNull(Map<String, Collection<T>> map, String key) {
    if (map.containsKey(key) && !map.get(key).isEmpty()) {
      return map.get(key).iterator().next();
    }
    return null;
  }
}
