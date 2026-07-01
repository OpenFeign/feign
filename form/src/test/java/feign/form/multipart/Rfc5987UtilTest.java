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
package feign.form.multipart;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Adapted from Apache CXF ({@code org.apache.cxf.attachment.Rfc5987UtilTest}). */
public class Rfc5987UtilTest {
  @ParameterizedTest
  @CsvSource(
      textBlock =
          """
                           foo-ä-€.html, foo-%c3%a4-%e2%82%ac.html
                           世界ーファイル 2.jpg, %e4%b8%96%e7%95%8c%e3%83%bc%e3%83%95%e3%82%a1%e3%82%a4%e3%83%ab%202.jpg,
                           foo.jpg, foo.jpg
                           """)
  void test(String input, String expected) throws UnsupportedEncodingException {
    assertEquals(expected, Rfc5987Util.encode(input, StandardCharsets.UTF_8.name()));

    assertEquals(input, Rfc5987Util.decode(expected, StandardCharsets.UTF_8.name()));
  }
}
