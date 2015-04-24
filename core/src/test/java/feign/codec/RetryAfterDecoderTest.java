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

import org.junit.Test;

import java.text.ParseException;

import feign.codec.ErrorDecoder.RetryAfterDecoder;

import static feign.codec.ErrorDecoder.RetryAfterDecoder.RFC822_FORMAT;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class RetryAfterDecoderTest {

  private RetryAfterDecoder decoder = new RetryAfterDecoder(RFC822_FORMAT) {
    protected long currentTimeMillis() {
      try {
        return RFC822_FORMAT.parse("Sat, 1 Jan 2000 00:00:00 GMT").getTime();
      } catch (ParseException e) {
        throw new RuntimeException(e);
      }
    }
  };

  @Test
  public void malformDateFailsGracefully() {
    assertFalse(decoder.apply("Fri, 31 Dec 1999 23:59:59 ZBW") != null);
  }

  @Test
  public void rfc822Parses() throws ParseException {
    assertEquals(RFC822_FORMAT.parse("Fri, 31 Dec 1999 23:59:59 GMT"),
                 decoder.apply("Fri, 31 Dec 1999 23:59:59 GMT"));
  }

  @Test
  public void relativeSecondsParses() throws ParseException {
    assertEquals(RFC822_FORMAT.parse("Sun, 2 Jan 2000 00:00:00 GMT"), decoder.apply("86400"));
  }
}
