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
package feign.codec;

import static feign.FeignException.errorStatus;
import static feign.Util.RETRY_AFTER;
import static feign.Util.checkNotNull;
import static java.util.Locale.US;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import feign.FeignException;
import feign.Response;
import feign.RetryableException;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * Allows you to massage an exception into a application-specific one, or fallback to a default
 * value. Falling back to null on {@link Response#status() status 404}, or converting out to a
 * throttle exception are examples of this in use. <br>
 * Ex. <br>
 *
 * <pre>
 * class IllegalArgumentExceptionOn404Decoder extends ErrorDecoder {
 *
 *     &#064;Override
 *     public Object decode(String methodKey, Response response, Type&lt;?&gt; type) throws Throwable {
 *    if (response.status() == 404)
 *        throw new IllegalArgumentException(&quot;zone not found&quot;);
 *    return ErrorDecoder.DEFAULT.decode(request, response, type);
 *     }
 *
 * }
 * </pre>
 */
public interface ErrorDecoder {

  /**
   * Implement this method in order to decode an HTTP {@link Response} when {@link
   * Response#status()} is not in the 2xx range. Please raise application-specific exceptions or
   * return fallback values where possible. If your exception is retryable, wrap or subclass {@link
   * RetryableException}
   *
   * @param methodKey {@link feign.Feign#configKey} of the java method that invoked the request. ex.
   *     {@code IAM#getUser()}
   * @param response HTTP response where {@link Response#status() status} is greater than or equal
   *     to {@code 300}.
   * @param type Target object type.
   * @return instance of {@code type}
   * @throws Throwable IOException, if there was a network error reading the response or an
   *     application-specific exception decoded by the implementation. If the throwable is
   *     retryable, it should be wrapped, or a subtype of {@link RetryableException}
   */
  public Object decode(String methodKey, Response response, Type type) throws Throwable;

  public static final ErrorDecoder DEFAULT =
      new ErrorDecoder() {

        private final RetryAfterDecoder retryAfterDecoder = new RetryAfterDecoder();

        @Override
        public Object decode(String methodKey, Response response, Type type) throws Throwable {
          FeignException exception = errorStatus(methodKey, response);
          Date retryAfter = retryAfterDecoder.apply(firstOrNull(response.headers(), RETRY_AFTER));
          if (retryAfter != null)
            throw new RetryableException(exception.getMessage(), exception, retryAfter);
          throw exception;
        }

        private <T> T firstOrNull(Map<String, Collection<T>> map, String key) {
          if (map.containsKey(key) && !map.get(key).isEmpty()) {
            return map.get(key).iterator().next();
          }
          return null;
        }
      };

  /**
   * Decodes a {@link feign.Util#RETRY_AFTER} header into an absolute date, if possible. <br>
   * See <a href="https://tools.ietf.org/html/rfc2616#section-14.37">Retry-After format</a>
   */
  static class RetryAfterDecoder {
    static final DateFormat RFC822_FORMAT =
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", US);
    private final DateFormat rfc822Format;

    RetryAfterDecoder() {
      this(RFC822_FORMAT);
    }

    protected long currentTimeNanos() {
      return System.currentTimeMillis();
    }

    RetryAfterDecoder(DateFormat rfc822Format) {
      this.rfc822Format = checkNotNull(rfc822Format, "rfc822Format");
    }

    /**
     * returns a date that corresponds to the first time a request can be retried.
     *
     * @param retryAfter String in <a href="https://tools.ietf.org/html/rfc2616#section-14.37"
     *     >Retry-After format</a>
     */
    public Date apply(String retryAfter) {
      if (retryAfter == null) return null;
      if (retryAfter.matches("^[0-9]+$")) {
        long currentTimeMillis = NANOSECONDS.toMillis(currentTimeNanos());
        long deltaMillis = SECONDS.toMillis(Long.parseLong(retryAfter));
        return new Date(currentTimeMillis + deltaMillis);
      }
      synchronized (rfc822Format) {
        try {
          return rfc822Format.parse(retryAfter);
        } catch (ParseException ignored) {
          return null;
        }
      }
    }
  }
}
