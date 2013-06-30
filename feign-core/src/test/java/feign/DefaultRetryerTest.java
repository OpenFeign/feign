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

import com.google.common.base.Ticker;

import org.testng.annotations.Test;

import java.util.Date;

import feign.Retryer.Default;

import static org.testng.Assert.assertEquals;

@Test
public class DefaultRetryerTest {

  @Test(expectedExceptions = RetryableException.class)
  public void only5TriesAllowedAndExponentialBackoff() throws Exception {
    RetryableException e = new RetryableException(null, null, null);
    Default retryer = new Retryer.Default();
    assertEquals(retryer.attempt, 1);
    assertEquals(retryer.sleptForNanos, 0);

    retryer.continueOrPropagate(e);
    assertEquals(retryer.attempt, 2);
    assertEquals(retryer.sleptForNanos, 150000000);

    retryer.continueOrPropagate(e);
    assertEquals(retryer.attempt, 3);
    assertEquals(retryer.sleptForNanos, 375000000);

    retryer.continueOrPropagate(e);
    assertEquals(retryer.attempt, 4);
    assertEquals(retryer.sleptForNanos, 712500000);

    retryer.continueOrPropagate(e);
    assertEquals(retryer.attempt, 5);
    assertEquals(retryer.sleptForNanos, 1218750000);

    retryer.continueOrPropagate(e);
    // fail
  }

  @Test public void considersRetryAfterButNotMoreThanMaxPeriod() throws Exception {
    Default retryer = new Retryer.Default();
    retryer.ticker = epoch;

    retryer.continueOrPropagate(new RetryableException(null, null, new Date(5000)));
    assertEquals(retryer.attempt, 2);
    assertEquals(retryer.sleptForNanos, 1000000000);
  }

  static Ticker epoch = new Ticker() {
    @Override
    public long read() {
      return 0;
    }
  };

}
