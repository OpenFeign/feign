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
package feign.codec;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import java.text.ParseException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.Test;
import feign.codec.ErrorDecoder.RetryAfterDecoder;

class RetryAfterDecoderTest {

  private final RetryAfterDecoder decoder = new RetryAfterDecoder(RFC_1123_DATE_TIME) {
    @Override
    protected long currentTimeMillis() {
      return parseDateTime("Sat, 1 Jan 2000 00:00:00 GMT");
    }
  };

  @Test
  void malformedDateFailsGracefully() {
    assertThat(decoder.apply("Fri, 31 Dec 1999 23:59:59 ZBW")).isNull();
  }

  @Test
  void rfc822Parses() throws ParseException {
    assertThat(decoder.apply("Fri, 31 Dec 1999 23:59:59 GMT"))
        .isEqualTo(parseDateTime("Fri, 31 Dec 1999 23:59:59 GMT"));
  }

  @Test
  void relativeSecondsParses() throws ParseException {
    assertThat(decoder.apply("86400")).isEqualTo(parseDateTime("Sun, 2 Jan 2000 00:00:00 GMT"));
  }

  @Test
  void relativeSecondsParseDecimalIntegers() throws ParseException {
    assertThat(decoder.apply("86400.0")).isEqualTo(parseDateTime("Sun, 2 Jan 2000 00:00:00 GMT"));
  }

  private Long parseDateTime(String text) {
    try {
      return ZonedDateTime.parse(text, RFC_1123_DATE_TIME).toInstant().toEpochMilli();
    } catch (NullPointerException | DateTimeParseException exception) {
      throw new RuntimeException(exception);
    }
  }
}
