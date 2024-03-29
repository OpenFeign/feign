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
package feign.template;


import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

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
}
