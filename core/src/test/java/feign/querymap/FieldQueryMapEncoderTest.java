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
package feign.querymap;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import feign.Param;
import feign.QueryMapEncoder;

/**
 * Test for {@link FieldQueryMapEncoder}
 */
class FieldQueryMapEncoderTest {

  private final QueryMapEncoder encoder = new FieldQueryMapEncoder();

  @Test
  void defaultEncoder_acceptNullValue() {
    assertThat(encoder.encode(null)).as("Empty map should be returned")
        .isEqualTo(Collections.EMPTY_MAP);
  }

  @Test
  void defaultEncoder_normalClassWithValues() {
    final Map<String, Object> expected = new HashMap<>();
    expected.put("foo", "fooz");
    expected.put("bar", "barz");
    final NormalObject normalObject = new NormalObject("fooz", "barz");

    final Map<String, Object> encodedMap = encoder.encode(normalObject);

    assertThat(encodedMap).as("Unexpected encoded query map").isEqualTo(expected);
  }

  @Test
  void defaultEncoder_normalClassWithOutValues() {
    final NormalObject normalObject = new NormalObject(null, null);

    final Map<String, Object> encodedMap = encoder.encode(normalObject);

    assertThat(encodedMap.isEmpty()).as("Non-empty map generated from null getter: " + encodedMap)
        .isTrue();
  }

  @Test
  void defaultEncoder_withOverriddenParamName() {
    HashSet<Object> expectedNames = new HashSet<>();
    expectedNames.add("fooAlias");
    expectedNames.add("bar");
    final NormalObjectWithOverriddenParamName normalObject =
        new NormalObjectWithOverriddenParamName("fooz", "barz");

    final Map<String, Object> encodedMap = encoder.encode(normalObject);

    assertThat(encodedMap.keySet()).as("@Param ignored").isEqualTo(expectedNames);
  }

  class NormalObject {

    private NormalObject(String foo, String bar) {
      this.foo = foo;
      this.bar = bar;
    }

    private final String foo;
    private final String bar;
  }

  class NormalObjectWithOverriddenParamName {

    private NormalObjectWithOverriddenParamName(String foo, String bar) {
      this.foo = foo;
      this.bar = bar;
    }

    @Param("fooAlias")
    private final String foo;
    private final String bar;
  }

}
