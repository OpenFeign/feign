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
package feign;

import static java.util.concurrent.TimeUnit.SECONDS;

public class DefaultRetryer implements Retryer {

  private final int maxAttempts;
  private final long period;
  private final long maxPeriod;
  int attempt;
  long sleptForMillis;

  public DefaultRetryer() {
    this(100, SECONDS.toMillis(1), 5);
  }

  public DefaultRetryer(long period, long maxPeriod, int maxAttempts) {
    this.period = period;
    this.maxPeriod = maxPeriod;
    this.maxAttempts = maxAttempts;
    this.attempt = 1;
  }

  // visible for testing;
  protected long currentTimeMillis() {
    return System.currentTimeMillis();
  }

  public void continueOrPropagate(RetryableException e) {
    if (attempt++ >= maxAttempts) {
      throw e;
    }

    long interval;
    if (e.retryAfter() != null) {
      interval = e.retryAfter() - currentTimeMillis();
      if (interval > maxPeriod) {
        interval = maxPeriod;
      }
      if (interval < 0) {
        return;
      }
    } else {
      interval = nextMaxInterval();
    }
    try {
      Thread.sleep(interval);
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
      throw e;
    }
    sleptForMillis += interval;
  }

  /**
   * Calculates the time interval to a retry attempt.<br>
   * The interval increases exponentially with each attempt, at a rate of nextInterval *= 1.5 (where
   * 1.5 is the backoff factor), to the maximum interval.
   *
   * @return time in milliseconds from now until the next attempt.
   */
  long nextMaxInterval() {
    long interval = (long) (period * Math.pow(1.5, attempt - 1));
    return Math.min(interval, maxPeriod);
  }

  @Override
  public Retryer clone() {
    return new DefaultRetryer(period, maxPeriod, maxAttempts);
  }
}
