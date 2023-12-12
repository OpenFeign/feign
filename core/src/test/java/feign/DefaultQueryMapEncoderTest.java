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

import static org.assertj.core.api.Assertions.assertThat;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import feign.querymap.FieldQueryMapEncoder;

class DefaultQueryMapEncoderTest {

  private final QueryMapEncoder encoder = new FieldQueryMapEncoder();

  @Test
  void encodesObject_visibleFields() {
    Map<String, Object> expected = new HashMap<>();
    expected.put("foo", "fooz");
    expected.put("bar", "barz");
    expected.put("baz", "bazz");
    VisibleFieldsObject object = new VisibleFieldsObject();
    object.foo = "fooz";
    object.bar = "barz";
    object.baz = "bazz";

    Map<String, Object> encodedMap = encoder.encode(object);
    assertThat(encodedMap).as("Unexpected encoded query map").isEqualTo(expected);
  }

  @Test
  void encodesObject_visibleFields_emptyObject() {
    VisibleFieldsObject object = new VisibleFieldsObject();
    Map<String, Object> encodedMap = encoder.encode(object);
    assertThat(encodedMap.isEmpty()).as("Non-empty map generated from null fields: " + encodedMap)
        .isTrue();
  }

  @Test
  void encodesObject_nonVisibleFields() {
    Map<String, Object> expected = new HashMap<>();
    expected.put("foo", "fooz");
    expected.put("bar", "barz");
    QueryMapEncoderObject object = new QueryMapEncoderObject("fooz", "barz");

    Map<String, Object> encodedMap = encoder.encode(object);
    assertThat(encodedMap).as("Unexpected encoded query map").isEqualTo(expected);
  }

  @Test
  void encodesObject_nonVisibleFields_emptyObject() {
    QueryMapEncoderObject object = new QueryMapEncoderObject(null, null);
    Map<String, Object> encodedMap = encoder.encode(object);
    assertThat(encodedMap.isEmpty()).as("Non-empty map generated from null fields").isTrue();
  }

  static class VisibleFieldsObject {
    String foo;
    String bar;
    String baz;
  }
}

