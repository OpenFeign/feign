package feign.hystrix;

import feign.FeignException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static feign.Util.checkNotNull;

/**
 * Used to control the fallback given its cause.
 *
 * Ex.
 * <pre>{@code
 * // This instance will be invoked if there are errors of any kind.
 * FallbackFactory<GitHub> fallbackFactory = cause -> (owner, repo) -> {
 *   if (cause instanceof FeignException && ((FeignException) cause).status() == 403) {
 *     return Collections.emptyList();
 *   } else {
 *     return Arrays.asList("yogi");
 *   }
 * };
 *
 * GitHub github = HystrixFeign.builder()
 *                             ...
 *                             .target(GitHub.class, "https://api.github.com", fallbackFactory);
 * }
 * </pre>
 *
 * @param <T> the feign interface type
 */
public interface FallbackFactory<T> {

  /**
   * Returns an instance of the fallback appropriate for the given cause
   *
   * @param cause corresponds to {@link com.netflix.hystrix.AbstractCommand#getFailedExecutionException()}
   * often, but not always an instance of {@link FeignException}.
   */
  T create(Throwable cause);

  /** Returns a constant fallback after logging the cause to FINE level. */
  final class Default<T> implements FallbackFactory<T> {
    // jul to not add a dependency
    final Logger logger;
    final T constant;

    public Default(T constant) {
      this(constant, Logger.getLogger(Default.class.getName()));
    }

    Default(T constant, Logger logger) {
      this.constant = checkNotNull(constant, "fallback");
      this.logger = checkNotNull(logger, "logger");
    }

    @Override
    public T create(Throwable cause) {
      if (logger.isLoggable(Level.FINE)) {
        logger.log(Level.FINE, "fallback due to: " + cause.getMessage(), cause);
      }
      return constant;
    }

    @Override
    public String toString() {
      return constant.toString();
    }
  }
}
