/*
 * Copyright 2012-2023 The Feign Authors
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
package feign.codec;

import static feign.FeignException.errorStatus;
import static feign.Util.RETRY_AFTER;
import static feign.Util.checkNotNull;
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.util.concurrent.TimeUnit.SECONDS;
import feign.FeignException;
import feign.Response;
import feign.RetryableException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Map;

/**
 * Allows you to massage an exception into a application-specific one. Converting out to a throttle
 * exception are examples of this in use.
 *
 * <p/>
 * Ex:
 *
 * <pre>
 * class IllegalArgumentExceptionOn404Decoder implements ErrorDecoder {
 *
 *   &#064;Override
 *   public Exception decode(String methodKey, Response response) {
 *     if (response.status() == 400)
 *       throw new IllegalArgumentException(&quot;bad zone name&quot;);
 *     return new ErrorDecoder.Default().decode(methodKey, response);
 *   }
 *
 * }
 * </pre>
 *
 * <p/>
 * <b>Error handling</b>
 *
 * <p/>
 * Responses where {@link Response#status()} is not in the 2xx range are classified as errors,
 * addressed by the {@link ErrorDecoder}. That said, certain RPC apis return errors defined in the
 * {@link Response#body()} even on a 200 status. For example, in the DynECT api, a job still running
 * condition is returned with a 200 status, encoded in json. When scenarios like this occur, you
 * should raise an application-specific exception (which may be {@link feign.RetryableException
 * retryable}).
 *
 * <p/>
 * <b>Not Found Semantics</b>
 * <p/>
 * It is commonly the case that 404 (Not Found) status has semantic value in HTTP apis. While the
 * default behavior is to raise exeception, users can alternatively enable 404 processing via
 * {@link feign.Feign.Builder#dismiss404()}.
 */
public interface ErrorDecoder {

  /**
   * Implement this method in order to decode an HTTP {@link Response} when
   * {@link Response#status()} is not in the 2xx range. Please raise application-specific exceptions
   * where possible. If your exception is retryable, wrap or subclass {@link RetryableException}
   *
   * @param methodKey {@link feign.Feign#configKey} of the java method that invoked the request. ex.
   *        {@code IAM#getUser()}
   * @param response HTTP response where {@link Response#status() status} is greater than or equal
   *        to {@code 300}.
   * @return Exception IOException, if there was a network error reading the response or an
   *         application-specific exception decoded by the implementation. If the throwable is
   *         retryable, it should be wrapped, or a subtype of {@link RetryableException}
   */
  public Exception decode(String methodKey, Response response);

  public class Default implements ErrorDecoder {

    private final RetryAfterDecoder retryAfterDecoder = new RetryAfterDecoder();
    private Integer maxBodyBytesLength;
    private Integer maxBodyCharsLength;

    public Default() {
      this.maxBodyBytesLength = null;
      this.maxBodyCharsLength = null;
    }

    public Default(Integer maxBodyBytesLength, Integer maxBodyCharsLength) {
      this.maxBodyBytesLength = maxBodyBytesLength;
      this.maxBodyCharsLength = maxBodyCharsLength;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
      FeignException exception = errorStatus(methodKey, response, maxBodyBytesLength,
          maxBodyCharsLength);
      Long retryAfter = retryAfterDecoder.apply(firstOrNull(response.headers(), RETRY_AFTER));
      if (retryAfter != null) {
        return new RetryableException(
            response.status(),
            exception.getMessage(),
            response.request().httpMethod(),
            exception,
            retryAfter,
            response.request());
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

  /**
   * Decodes a {@link feign.Util#RETRY_AFTER} header into an epoch millisecond, if possible.<br>
   * See <a href="https://tools.ietf.org/html/rfc2616#section-14.37">Retry-After format</a>
   */
  static class RetryAfterDecoder {

    private final DateTimeFormatter dateTimeFormatter;

    RetryAfterDecoder() {
      this(RFC_1123_DATE_TIME);
    }

    RetryAfterDecoder(DateTimeFormatter dateTimeFormatter) {
      this.dateTimeFormatter = checkNotNull(dateTimeFormatter, "dateTimeFormatter");
    }

    protected long currentTimeMillis() {
      return System.currentTimeMillis();
    }

    /**
     * returns an epoch millisecond that corresponds to the first time a request can be retried.
     *
     * @param retryAfter String in
     *        <a href="https://tools.ietf.org/html/rfc2616#section-14.37" >Retry-After format</a>
     */
    public Long apply(String retryAfter) {
      if (retryAfter == null) {
        return null;
      }
      if (retryAfter.matches("^[0-9]+\\.?0*$")) {
        retryAfter = retryAfter.replaceAll("\\.0*$", "");
        long deltaMillis = SECONDS.toMillis(Long.parseLong(retryAfter));
        return currentTimeMillis() + deltaMillis;
      }
      try {
        return ZonedDateTime.parse(retryAfter, dateTimeFormatter).toInstant().toEpochMilli();
      } catch (NullPointerException | DateTimeParseException ignored) {
        return null;
      }
    }
  }
}
