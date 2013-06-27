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
    assertEquals(retryer.sleptForNanos, 75000000);

    retryer.continueOrPropagate(e);
    assertEquals(retryer.attempt, 3);
    assertEquals(retryer.sleptForNanos, 187500000);

    retryer.continueOrPropagate(e);
    assertEquals(retryer.attempt, 4);
    assertEquals(retryer.sleptForNanos, 356250000);

    retryer.continueOrPropagate(e);
    assertEquals(retryer.attempt, 5);
    assertEquals(retryer.sleptForNanos, 609375000);

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
