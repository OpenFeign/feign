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
package feign.querymap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import java.util.HashMap;
import java.util.Map;
import feign.QueryMapEncoder;

/**
 * Test for {@link FieldQueryMapEncoder}
 */
public class FieldQueryMapEncoderTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  private final QueryMapEncoder encoder = new FieldQueryMapEncoder();

  @Test
  public void testDefaultEncoder_normalClassWithValues() {
    final Map<String, Object> expected = new HashMap<>();
    expected.put("foo", "fooz");
    expected.put("bar", "barz");
    final NormalObject normalObject = new NormalObject("fooz", "barz");

    final Map<String, Object> encodedMap = encoder.encode(normalObject);

    assertEquals("Unexpected encoded query map", expected, encodedMap);
  }

  @Test
  public void testDefaultEncoder_normalClassWithOutValues() {
    final NormalObject normalObject = new NormalObject(null, null);

    final Map<String, Object> encodedMap = encoder.encode(normalObject);

    assertTrue("Non-empty map generated from null getter: " + encodedMap, encodedMap.isEmpty());
  }

  class NormalObject {

    private NormalObject(String foo, String bar) {
      this.foo = foo;
      this.bar = bar;
    }

    private final String foo;
    private final String bar;
  }

}
