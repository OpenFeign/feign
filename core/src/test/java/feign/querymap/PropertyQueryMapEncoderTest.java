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

import feign.QueryMapEncoder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PropertyQueryMapEncoderTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  private final QueryMapEncoder encoder = new BeanQueryMapEncoder();

  @Test
  public void testDefaultEncoder_normalClassWithValues() {
    Map<String, Object> expected = new HashMap<>();
    expected.put("foo", "fooz");
    expected.put("bar", "barz");
    expected.put("fooAppendBar", "foozbarz");
    NormalObject normalObject = new NormalObject("fooz", "barz");

    Map<String, Object> encodedMap = encoder.encode(normalObject);

    assertEquals("Unexpected encoded query map", expected, encodedMap);
  }

  @Test
  public void testDefaultEncoder_normalClassWithOutValues() {
    NormalObject normalObject = new NormalObject(null, null);

    Map<String, Object> encodedMap = encoder.encode(normalObject);

    assertTrue("Non-empty map generated from null getter: " + encodedMap, encodedMap.isEmpty());
  }

  @Test
  public void testDefaultEncoder_haveSuperClass() {
    Map<String, Object> expected = new HashMap<>();
    expected.put("page", 1);
    expected.put("size", 10);
    expected.put("query", "queryString");
    SubClass subClass = new SubClass();
    subClass.setPage(1);
    subClass.setSize(10);
    subClass.setQuery("queryString");

    Map<String, Object> encodedMap = encoder.encode(subClass);

    assertEquals("Unexpected encoded query map", expected, encodedMap);
  }


  class NormalObject {

    private NormalObject(String foo, String bar) {
      this.foo = foo;
      this.bar = bar;
    }

    private String foo;
    private String bar;

    public String getFoo() {
      return foo;
    }

    public String getBar() {
      return bar;
    }

    public String getFooAppendBar() {
      if (foo != null && bar != null) {
        return foo + bar;
      }
      return null;
    }
  }

  class SuperClass {
    private int page;
    private int size;

    public int getPage() {
      return page;
    }

    public void setPage(int page) {
      this.page = page;
    }

    public int getSize() {
      return size;
    }

    public void setSize(int size) {
      this.size = size;
    }
  }

  class SubClass extends SuperClass {

    private String query;

    public String getQuery() {
      return query;
    }

    public void setQuery(String query) {
      this.query = query;
    }
  }
}
