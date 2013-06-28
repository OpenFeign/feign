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
