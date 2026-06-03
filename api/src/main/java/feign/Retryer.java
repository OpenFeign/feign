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

/**
 * Cloned for each invocation to {@link Client#execute(Request, feign.Request.Options)}.
 * Implementations may keep state to determine if retry operations should continue or not.
 */
public interface Retryer extends Cloneable {

  /**
   * if retry is permitted, return (possibly after sleeping). Otherwise, propagate the exception.
   */
  void continueOrPropagate(RetryableException e);

  Retryer clone();

  /**
   * @deprecated use {@link DefaultRetryer} instead.
   */
  @Deprecated
  class Default implements Retryer {
    private final Retryer delegate;

    public Default() {
      Retryer temp = null;
      try {
        temp =
            (Retryer) Class.forName("feign.DefaultRetryer").getDeclaredConstructor().newInstance();
      } catch (Exception e) {
        // ignore
      }
      this.delegate = temp;
    }

    public Default(long period, long maxPeriod, int maxAttempts) {
      Retryer temp = null;
      try {
        temp =
            (Retryer)
                Class.forName("feign.DefaultRetryer")
                    .getConstructor(long.class, long.class, int.class)
                    .newInstance(period, maxPeriod, maxAttempts);
      } catch (Exception e) {
        // ignore
      }
      this.delegate = temp;
    }

    @Override
    public void continueOrPropagate(RetryableException e) {
      if (delegate != null) {
        delegate.continueOrPropagate(e);
      } else {
        throw e;
      }
    }

    @Override
    public Retryer clone() {
      if (delegate != null) {
        return delegate.clone();
      }
      return this;
    }
  }

  /** Implementation that never retries request. It propagates the RetryableException. */
  Retryer NEVER_RETRY =
      new Retryer() {

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
