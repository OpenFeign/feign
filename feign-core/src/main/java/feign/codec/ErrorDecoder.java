package feign.codec;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.getFirst;
import static com.google.common.net.HttpHeaders.RETRY_AFTER;
import static feign.FeignException.errorStatus;
import static java.util.Locale.US;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Ticker;
import com.google.common.net.HttpHeaders;
import com.google.common.reflect.TypeToken;
import feign.FeignException;
import feign.Response;
import feign.RetryableException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Allows you to massage an exception into a application-specific one, or fallback to a default
 * value. Falling back to null on {@link Response#status() status 404}, or converting out to a
 * throttle exception are examples of this in use.
 *
 * <p>Ex.
 *
 * <p>
 *
 * <pre>
 * class IllegalArgumentExceptionOn404Decoder extends ErrorDecoder {
 *
 *     &#064;Override
 *     public Object decode(String methodKey, Response response, TypeToken&lt;?&gt; type) throws Throwable {
 *         if (response.status() == 404)
 *             throw new IllegalArgumentException(&quot;zone not found&quot;);
 *         return ErrorDecoder.DEFAULT.decode(request, response, type);
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
   * @param response HTTP response where {@link Response#status() status} >= {@code 300}.
   * @param type Target object type.
   * @return instance of {@code type}
   * @throws Throwable IOException, if there was a network error reading the response or an
   *     application-specific exception decoded by the implementation. If the throwable is
   *     retryable, it should be wrapped, or a subtype of {@link RetryableException}
   */
  public Object decode(String methodKey, Response response, TypeToken<?> type) throws Throwable;

  public static final ErrorDecoder DEFAULT =
      new ErrorDecoder() {

        private final RetryAfterDecoder retryAfterDecoder = new RetryAfterDecoder();

        @Override
        public Object decode(String methodKey, Response response, TypeToken<?> type)
            throws Throwable {
          FeignException exception = errorStatus(methodKey, response);
          Optional<Date> retryAfter =
              retryAfterDecoder.apply(getFirst(response.headers().get(RETRY_AFTER), null));
          if (retryAfter.isPresent())
            throw new RetryableException(exception.getMessage(), exception, retryAfter.get());
          throw exception;
        }
      };

  /**
   * Decodes a {@link HttpHeaders#RETRY_AFTER} header into an absolute date, if possible.
   *
   * @see <a href="https://tools.ietf.org/html/rfc2616#section-14.37">Retry-After format</a>
   */
  static class RetryAfterDecoder implements Function<String, Optional<Date>> {
    static final DateFormat RFC822_FORMAT =
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", US);
    private final Ticker currentTimeNanos;
    private final DateFormat rfc822Format;

    RetryAfterDecoder() {
      this(Ticker.systemTicker(), RFC822_FORMAT);
    }

    RetryAfterDecoder(Ticker currentTimeNanos, DateFormat rfc822Format) {
      this.currentTimeNanos = checkNotNull(currentTimeNanos, "currentTimeNanos");
      this.rfc822Format = checkNotNull(rfc822Format, "rfc822Format");
    }

    /**
     * returns a date that corresponds to the first time a request can be retried.
     *
     * @param retryAfter String in <a href="https://tools.ietf.org/html/rfc2616#section-14.37"
     *     >Retry-After format</a>
     */
    @Override
    public Optional<Date> apply(String retryAfter) {
      if (retryAfter == null) return Optional.absent();
      if (retryAfter.matches("^[0-9]+$")) {
        long currentTimeMillis = NANOSECONDS.toMillis(currentTimeNanos.read());
        long deltaMillis = SECONDS.toMillis(Long.parseLong(retryAfter));
        return Optional.of(new Date(currentTimeMillis + deltaMillis));
      }
      synchronized (rfc822Format) {
        try {
          return Optional.of(rfc822Format.parse(retryAfter));
        } catch (ParseException ignored) {
          return Optional.absent();
        }
      }
    }
  }
}
