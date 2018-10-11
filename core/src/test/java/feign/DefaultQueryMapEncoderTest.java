/**
 * Copyright 2012-2018 The Feign Authors
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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DefaultQueryMapEncoderTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  private final QueryMapEncoder encoder = new QueryMapEncoder.Default();

  @Test
  public void testEncodesObject_visibleFields() {
    Map<String, Object> expected = new HashMap<>();
    expected.put("foo", "fooz");
    expected.put("bar", "barz");
    expected.put("baz", "bazz");
    VisibleFieldsObject object = new VisibleFieldsObject();
    object.foo = "fooz";
    object.bar = "barz";
    object.baz = "bazz";

    Map<String, Object> encodedMap = encoder.encode(object);
    assertEquals("Unexpected encoded query map", expected, encodedMap);
  }

  @Test
  public void testEncodesObject_visibleFields_emptyObject() {
    VisibleFieldsObject object = new VisibleFieldsObject();
    Map<String, Object> encodedMap = encoder.encode(object);
    assertTrue("Non-empty map generated from null fields: " + encodedMap, encodedMap.isEmpty());
  }

  @Test
  public void testEncodesObject_nonVisibleFields() {
    Map<String, Object> expected = new HashMap<>();
    expected.put("foo", "fooz");
    expected.put("bar", "barz");
    QueryMapEncoderObject object = new QueryMapEncoderObject("fooz", "barz");

    Map<String, Object> encodedMap = encoder.encode(object);
    assertEquals("Unexpected encoded query map", expected, encodedMap);
  }

  @Test
  public void testEncodesObject_nonVisibleFields_emptyObject() {
    QueryMapEncoderObject object = new QueryMapEncoderObject(null, null);
    Map<String, Object> encodedMap = encoder.encode(object);
    assertTrue("Non-empty map generated from null fields", encodedMap.isEmpty());
  }

  static class VisibleFieldsObject {
    String foo;
    String bar;
    String baz;
  }
}

