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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Date;

import feign.Retryer.Default;

import static org.junit.Assert.assertEquals;

public class RetryerTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void only5TriesAllowedAndExponentialBackoff() throws Exception {
    RetryableException e = new RetryableException(null, null, null);
    Default retryer = new Retryer.Default();
    assertEquals(1, retryer.attempt);
    assertEquals(0, retryer.sleptForMillis);

    retryer.continueOrPropagate(e);
    assertEquals(2, retryer.attempt);
    assertEquals(150, retryer.sleptForMillis);

    retryer.continueOrPropagate(e);
    assertEquals(3, retryer.attempt);
    assertEquals(375, retryer.sleptForMillis);

    retryer.continueOrPropagate(e);
    assertEquals(4, retryer.attempt);
    assertEquals(712, retryer.sleptForMillis);

    retryer.continueOrPropagate(e);
    assertEquals(5, retryer.attempt);
    assertEquals(1218, retryer.sleptForMillis);

    thrown.expect(RetryableException.class);
    retryer.continueOrPropagate(e);
  }

  @Test
  public void considersRetryAfterButNotMoreThanMaxPeriod() throws Exception {
    Default retryer = new Retryer.Default() {
      protected long currentTimeMillis() {
        return 0;
      }
    };

    retryer.continueOrPropagate(new RetryableException(null, null, new Date(5000)));
    assertEquals(2, retryer.attempt);
    assertEquals(1000, retryer.sleptForMillis);
  }

  @Test(expected = RetryableException.class)
  public void neverRetryAlwaysPropagates() {
    Retryer.NEVER_RETRY.continueOrPropagate(new RetryableException(null, null, new Date(5000)));
  }
}
