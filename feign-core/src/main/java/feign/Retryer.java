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

import static com.google.common.primitives.Longs.max;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.base.Ticker;

/**
 * Created for each invocation to {@link Client#execute(Request, feign.Request.Options)}.
 * Implementations may keep state to determine if retry operations should continue or not.
 */
public interface Retryer {

  /** if retry is permitted, return (possibly after sleeping). Otherwise propagate the exception. */
  void continueOrPropagate(RetryableException e);

  public static class Default implements Retryer {
    private final int maxAttempts = 5;
    private final long period = MILLISECONDS.toNanos(50);
    private final long maxPeriod = SECONDS.toNanos(1);

    // visible for testing;
    Ticker ticker = Ticker.systemTicker();
    int attempt;
    long sleptForNanos;

    public Default() {
      this.attempt = 1;
    }

    public void continueOrPropagate(RetryableException e) {
      if (attempt++ >= maxAttempts) throw e;

      long interval;
      if (e.retryAfter().isPresent()) {
        interval = max(maxPeriod, e.retryAfter().get().getTime() - ticker.read(), 0);
      } else {
        interval = nextMaxInterval();
      }
      sleepUninterruptibly(interval, NANOSECONDS);
      sleptForNanos += interval;
    }

    /**
     * Calculates the time interval to a retry attempt.
     *
     * <p>The interval increases exponentially with each attempt, at a rate of nextInterval *= 1.5
     * (where 1.5 is the backoff factor), to the maximum interval.
     *
     * @return time in nanoseconds from now until the next attempt.
     */
    long nextMaxInterval() {
      long interval = (long) (period * Math.pow(1.5, attempt - 1));
      return interval > maxPeriod ? maxPeriod : interval;
    }
  }
}
