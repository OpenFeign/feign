/*
 * Copyright 2012-2022 The Feign Authors
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

import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.Date;

/**
 * Cloned for each invocation to {@link Client#execute(Request, feign.Request.Options)}.
 * Implementations may keep state to determine if retry operations should continue or not.
 */
public interface Retryer extends Cloneable {

  /**
   * if retry is permitted, return (possibly after sleeping). Otherwise propagate the exception.
   */
  void continueOrPropagate(RetryableException e);

  Retryer clone();

  class Default implements Retryer {

    private final int maxAttempts;
    private final long period;
    private final long maxPeriod;
    int attempt;
    long sleptForMillis;

    public Default() {
      this(100, SECONDS.toMillis(1), 5);
    }

    public Default(long period, long maxPeriod, int maxAttempts) {
      this.period = period;
      this.maxPeriod = maxPeriod;
      this.maxAttempts = maxAttempts;
      this.attempt = 1;
    }

    @Override
    public void continueOrPropagate(RetryableException e) {
      if (attempt >= maxAttempts) {
        throw e;
      }

      long interval;
      if (e.retryAfter() == null) {
        interval = nextMaxInterval();
      } else {
        interval = intervalFromRetryAfter(e.retryAfter());

        if (interval < 0) {
          attempt++;
          return;
        }
      }

      try {
        Thread.sleep(interval);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
        throw e;
      }

      sleptForMillis += interval;
      attempt++;
    }

    @Override
    public Retryer clone() {
      return new Default(period, maxPeriod, maxAttempts);
    }

    /**
     * Calculates the time interval to a retry attempt. <br>
     * The interval increases exponentially with each attempt, at a rate of nextInterval *= 1.5
     * (where 1.5 is the backoff factor), to the maximum interval.
     *
     * @return time in milliseconds from now until the next attempt.
     */
    long nextMaxInterval() {
      long interval = (long) (period * Math.pow(1.5, attempt - 1.0));

      return Math.min(interval, maxPeriod);
    }

    /**
     * Calculates the time interval to a retry attempt when a Retry-After date header is provided.
     * <br>
     * The interval is given as the difference in milliseconds between the retryAfter date and the
     * current date. If the retryAfter date is in the past, the interval is negative.
     *
     * @return time in milliseconds from now until the next attempt or negative amount if retryAfter
     *         is in the past.
     */
    long intervalFromRetryAfter(Date retryAfter) {
      long interval = retryAfter.getTime() - currentTimeMillis();

      return Math.min(interval, maxPeriod);
    }

    // visible for testing;
    protected long currentTimeMillis() {
      return System.currentTimeMillis();
    }
  }

  /**
   * Implementation that never retries request. It propagates the RetryableException.
   */
  Retryer NEVER_RETRY = new Retryer() {

    @Override
    public void continueOrPropagate(RetryableException e) {
      throw e;
    }

    @Override
    public Retryer clone() {
      return this;
    }
  };
}
