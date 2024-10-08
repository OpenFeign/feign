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
package feign.querymap;

import static org.assertj.core.api.Assertions.assertThat;

import feign.Param;
import feign.QueryMapEncoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Test for {@link BeanQueryMapEncoder} */
class BeanQueryMapEncoderTest {

  private final QueryMapEncoder encoder = new BeanQueryMapEncoder();

  @Test
  void defaultEncoder_acceptNullValue() {
    assertThat(encoder.encode(null))
        .as("Empty map should be returned")
        .isEqualTo(Collections.EMPTY_MAP);
  }

  @Test
  void defaultEncoder_normalClassWithValues() {
    Map<String, Object> expected = new HashMap<>();
    expected.put("foo", "fooz");
    expected.put("bar", "barz");
    expected.put("fooAppendBar", "foozbarz");
    NormalObject normalObject = new NormalObject("fooz", "barz");

    Map<String, Object> encodedMap = encoder.encode(normalObject);

    assertThat(encodedMap).as("Unexpected encoded query map").isEqualTo(expected);
  }

  @Test
  void defaultEncoder_normalClassWithOutValues() {
    NormalObject normalObject = new NormalObject(null, null);

    Map<String, Object> encodedMap = encoder.encode(normalObject);

    assertThat(encodedMap.isEmpty())
        .as("Non-empty map generated from null getter: " + encodedMap)
        .isTrue();
  }

  @Test
  void defaultEncoder_haveSuperClass() {
    Map<String, Object> expected = new HashMap<>();
    expected.put("page", 1);
    expected.put("size", 10);
    expected.put("query", "queryString");
    SubClass subClass = new SubClass();
    subClass.setPage(1);
    subClass.setSize(10);
    subClass.setQuery("queryString");

    Map<String, Object> encodedMap = encoder.encode(subClass);

    assertThat(encodedMap).as("Unexpected encoded query map").isEqualTo(expected);
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

  class NormalObjectWithOverriddenParamName {

    private NormalObjectWithOverriddenParamName(String foo, String bar) {
      this.foo = foo;
      this.bar = bar;
    }

    private String foo;
    private String bar;

    @Param("fooAlias")
    public String getFoo() {
      return foo;
    }

    public String getBar() {
      return bar;
    }
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
