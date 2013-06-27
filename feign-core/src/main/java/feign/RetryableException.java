package feign;

import com.google.common.base.Optional;
import com.google.common.net.HttpHeaders;
import feign.codec.ErrorDecoder;
import java.util.Date;

/**
 * This exception is raised when the {@link Response} is deemed to be retryable, typically via an
 * {@link ErrorDecoder} when the {@link Response#status() status} is 503.
 */
public class RetryableException extends FeignException {

  private static final long serialVersionUID = 1L;

  private final Optional<Date> retryAfter;

  /**
   * @param retryAfter usually corresponds to the {@link HttpHeaders#RETRY_AFTER} header.
   */
  public RetryableException(String message, Throwable cause, Date retryAfter) {
    super(message, cause);
    this.retryAfter = Optional.fromNullable(retryAfter);
  }

  /**
   * @param retryAfter usually corresponds to the {@link HttpHeaders#RETRY_AFTER} header.
   */
  public RetryableException(String message, Date retryAfter) {
    super(message);
    this.retryAfter = Optional.fromNullable(retryAfter);
  }

  /**
   * Sometimes corresponds to the {@link HttpHeaders#RETRY_AFTER} header present in {@code 503}
   * status. Other times parsed from an application-specific response.
   */
  public Optional<Date> retryAfter() {
    return retryAfter;
  }
}
