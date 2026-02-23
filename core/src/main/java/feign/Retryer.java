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
  class Default extends DefaultRetryer {

    public Default() {
      super();
    }

    public Default(long period, long maxPeriod, int maxAttempts) {
      super(period, maxPeriod, maxAttempts);
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
