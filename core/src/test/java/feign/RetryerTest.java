/*
 * Copyright 2012-2023 The Feign Authors
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import feign.Retryer.Default;

@SuppressWarnings("deprecation")
class RetryerTest {

  private final static Request REQUEST =
      Request.create(Request.HttpMethod.GET, "/", Collections.emptyMap(), null,
          Util.UTF_8);

  @Test
  void only5TriesAllowedAndExponentialBackoff() {
    final Long nonRetryable = null;
    RetryableException e = new RetryableException(-1, null, null, nonRetryable, REQUEST);
    Default retryer = new Retryer.Default();
    assertThat(retryer.attempt).isEqualTo(1);
    assertThat(retryer.sleptForMillis).isEqualTo(0);

    retryer.continueOrPropagate(e);
    assertThat(retryer.attempt).isEqualTo(2);
    assertThat(retryer.sleptForMillis).isEqualTo(150);

    retryer.continueOrPropagate(e);
    assertThat(retryer.attempt).isEqualTo(3);
    assertThat(retryer.sleptForMillis).isEqualTo(375);

    retryer.continueOrPropagate(e);
    assertThat(retryer.attempt).isEqualTo(4);
    assertThat(retryer.sleptForMillis).isEqualTo(712);

    retryer.continueOrPropagate(e);
    assertThat(retryer.attempt).isEqualTo(5);
    assertThat(retryer.sleptForMillis).isEqualTo(1218);
    assertThrows(RetryableException.class, () -> retryer.continueOrPropagate(e));
  }

  @Test
  void considersRetryAfterButNotMoreThanMaxPeriod() {
    Default retryer = new Retryer.Default() {
      @Override
      protected long currentTimeMillis() {
        return 0;
      }
    };

    retryer.continueOrPropagate(new RetryableException(-1, null, null, 5000L, REQUEST));
    assertThat(retryer.attempt).isEqualTo(2);
    assertThat(retryer.sleptForMillis).isEqualTo(1000);
  }

  @Test
  void neverRetryAlwaysPropagates() {
    assertThrows(RetryableException.class,
        () -> Retryer.NEVER_RETRY
            .continueOrPropagate(new RetryableException(-1, null, null, 5000L, REQUEST)));
  }

  @Test
  void defaultRetryerFailsOnInterruptedException() {
    Default retryer = new Retryer.Default();

    Thread.currentThread().interrupt();
    RetryableException expected =
        new RetryableException(-1, null, null, System.currentTimeMillis() + 5000,
            REQUEST);
    try {
      retryer.continueOrPropagate(expected);
      Thread.interrupted(); // reset interrupted flag in case it wasn't
      fail("Retryer continued despite interruption");
    } catch (RetryableException e) {
      assertThat(Thread.interrupted()).as("Interrupted status not reset").isTrue();
      assertThat(retryer.attempt).as("Retry attempt not registered as expected").isEqualTo(2);
      assertThat(e).as("Unexpected exception found").isEqualTo(expected);
    }
  }
}
