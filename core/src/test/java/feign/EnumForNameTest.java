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

import static feign.Util.enumForName;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.Arrays;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import feign.Request.ProtocolVersion;

public class EnumForNameTest {

  @Nested
  public static class KnownEnumValues {
    public Object name;
    public ProtocolVersion expectedProtocolVersion;

    public static Iterable<Object[]> data() {
      return Arrays.asList(new Object[][] {
          {ProtocolVersion.HTTP_1_0, ProtocolVersion.HTTP_1_0},
          {"HTTP/1.0", ProtocolVersion.HTTP_1_0},
          {ProtocolVersion.HTTP_1_1, ProtocolVersion.HTTP_1_1},
          {"HTTP/1.1", ProtocolVersion.HTTP_1_1},
          {ProtocolVersion.HTTP_2, ProtocolVersion.HTTP_2},
          {"HTTP/2.0", ProtocolVersion.HTTP_2}
      });
    }

    @MethodSource("data")
    @ParameterizedTest
    void getKnownEnumValue(Object name, ProtocolVersion expectedProtocolVersion) {
      initKnownEnumValues(name, expectedProtocolVersion);
      assertThat(enumForName(ProtocolVersion.class, name)).as("known enum value: " + name)
          .isEqualTo(expectedProtocolVersion);
    }

    public void initKnownEnumValues(Object name, ProtocolVersion expectedProtocolVersion) {
      this.name = name;
      this.expectedProtocolVersion = expectedProtocolVersion;
    }

  }

  @Nested
  public static class UnknownEnumValues {

    public Object name;

    public static Iterable<Object[]> data() {
      return Arrays.asList(new Object[][] {
          {Request.HttpMethod.GET},
          {"SPDY/3"},
          {null},
          {"HTTP/2"}
      });
    }

    @MethodSource("data")
    @ParameterizedTest
    void getKnownEnumValue(Object name) {
      initUnknownEnumValues(name);
      assertThat(enumForName(ProtocolVersion.class, name)).as("unknown enum value: " + name)
          .isNull();
    }

    public void initUnknownEnumValues(Object name) {
      this.name = name;
    }

  }

}
