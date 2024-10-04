/*
 * Copyright 2012-2024 The Feign Authors
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
package feign.template;


import feign.Param;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class UriUtilsTest {

  /**
   * pct-encode a String, ensuring that all reserved characters are encoded.
   */
  @Test
  void pctEncode() {
    String queryParameterValue = "firstName=James;lastName=Bond;location=England&Britain?";
    assertThat(UriUtils.encode(queryParameterValue, UTF_8))
        .isEqualToIgnoringCase(
            "firstName%3DJames%3BlastName%3DBond%3Blocation%3DEngland%26Britain%3F");
  }


  /**
   * pct-encode preserving reserved characters.
   */
  @Test
  void pctEncodeWithReservedCharacters() {
    String withReserved = "/api/user@host:port#section[a-z]/data";
    String encoded = UriUtils.encode(withReserved, UTF_8, true);
    assertThat(encoded).isEqualTo("/api/user@host:port#section[a-z]/data");
  }

  @ParameterizedTest
  @MethodSource("provideValuesToEncode")
  void testVariousEncodingScenarios(String input, String expected) {
    assertThat(UriUtils.encode(input, UTF_8)).isEqualTo(expected);
  }

  private static Stream<Arguments> provideValuesToEncode() {
    return Stream.of(
        Arguments.of("foo", "foo"),
        Arguments.of("foo bar", "foo%20bar"),
        Arguments.of("foo%20bar", "foo%20bar"),
        Arguments.of("foo%2520bar", "foo%2520bar"),
        Arguments.of("foo&bar", "foo%26bar"),
        Arguments.of("foo& bar", "foo%26%20bar"),
        Arguments.of("foo = bar", "foo%20%3D%20bar"),
        Arguments.of("foo   ", "foo%20%20%20"),
        Arguments.of("foo/bar", "foo%2Fbar"),
        Arguments.of("foo!\"/$%?&   *( )  _%20^¨  >`:É.   ',.  é ;`^ ¸< nasty stuff here!",
            "foo%21%22%2F%24%25%3F%26%20%20%20%2A%28%20%29%20%20_%2520%5E%C2%A8%20%20%3E%60%3A%C3%89.%20%20%20%27%2C.%20%20%C3%A9%20%3B%60%5E%20%C2%B8%3C%20nasty%20stuff%20here%21"));

  }
}
