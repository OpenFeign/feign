package feign.codec;

import com.google.common.base.Ticker;

import org.testng.annotations.Test;

import java.text.ParseException;

import feign.codec.ErrorDecoder.RetryAfterDecoder;

import static feign.codec.ErrorDecoder.RetryAfterDecoder.RFC822_FORMAT;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

public class RetryAfterDecoderTest {

  @Test public void malformDateFailsGracefully() {
    assertFalse(decoder.apply("Fri, 31 Dec 1999 23:59:59 ZBW").isPresent());
  }

  @Test public void rfc822Parses() throws ParseException {
    assertEquals(decoder.apply("Fri, 31 Dec 1999 23:59:59 GMT").get(),
        RFC822_FORMAT.parse("Fri, 31 Dec 1999 23:59:59 GMT"));
  }

  @Test public void relativeSecondsParses() throws ParseException {
    assertEquals(decoder.apply("86400").get(), RFC822_FORMAT.parse("Sun, 2 Jan 2000 00:00:00 GMT"));
  }

  static Ticker y2k = new Ticker() {

    @Override
    public long read() {
      try {
        return MILLISECONDS.toNanos(RFC822_FORMAT.parse("Sat, 1 Jan 2000 00:00:00 GMT").getTime());
      } catch (ParseException e) {
        throw new RuntimeException(e);
      }
    }

  };

  private RetryAfterDecoder decoder = new RetryAfterDecoder(y2k, RFC822_FORMAT);

}
