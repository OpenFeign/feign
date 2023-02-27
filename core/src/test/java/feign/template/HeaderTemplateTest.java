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

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class HeaderTemplateTest {

  @Test
  public void it_should_throw_exception_when_name_is_null() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> HeaderTemplate.create(null, Collections.singletonList("test")));
    assertEquals("name is required.", exception.getMessage());
  }

  @Test
  public void it_should_throw_exception_when_name_is_empty() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> HeaderTemplate.create("", Collections.singletonList("test")));
    assertEquals("name is required.", exception.getMessage());
  }

  @Test
  public void it_should_throw_exception_when_value_is_null() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> HeaderTemplate.create("test", null));
    assertEquals("values are required", exception.getMessage());
  }

  @Test
  public void it_should_return_name() {
    HeaderTemplate headerTemplate =
        HeaderTemplate.create("test", Arrays.asList("test 1", "test 2"));
    assertEquals("test", headerTemplate.getName());
  }

  @Test
  public void it_should_return_expanded() {
    HeaderTemplate headerTemplate =
        HeaderTemplate.create("hello", Arrays.asList("emre", "savci", "{name}", "{missing}"));
    assertEquals("emre, savci", headerTemplate.expand(Collections.emptyMap()));
    assertEquals("emre, savci, firsts",
        headerTemplate.expand(Collections.singletonMap("name", "firsts")));
  }

  @Test
  public void it_should_return_expanded_literals() {
    HeaderTemplate headerTemplate =
        HeaderTemplate.create("hello", Arrays.asList("emre", "savci", "{replace_me}"));
    assertEquals("emre, savci, {}",
        headerTemplate.expand(Collections.singletonMap("replace_me", "{}")));
  }

  @Test
  public void create_should_preserve_order() {
    /*
     * Since Java 7, HashSet order is stable within a since JVM process, so one of these assertions
     * should fail if a HashSet is used.
     */
    HeaderTemplate headerTemplateWithFirstOrdering =
        HeaderTemplate.create("hello", Arrays.asList("test 1", "test 2"));
    assertThat(new ArrayList<>(headerTemplateWithFirstOrdering.getValues()),
        equalTo(Arrays.asList("test 1", "test 2")));

    HeaderTemplate headerTemplateWithSecondOrdering =
        HeaderTemplate.create("hello", Arrays.asList("test 2", "test 1"));
    assertThat(new ArrayList<>(headerTemplateWithSecondOrdering.getValues()),
        equalTo(Arrays.asList("test 2", "test 1")));
  }

  @Test
  public void append_should_preserve_order() {
    /*
     * Since Java 7, HashSet order is stable within a since JVM process, so one of these assertions
     * should fail if a HashSet is used.
     */
    HeaderTemplate headerTemplateWithFirstOrdering =
        HeaderTemplate.append(HeaderTemplate.create("hello", Collections.emptyList()),
            Arrays.asList("test 1", "test 2"));
    assertThat(new ArrayList<>(headerTemplateWithFirstOrdering.getValues()),
        equalTo(Arrays.asList("test 1", "test 2")));

    HeaderTemplate headerTemplateWithSecondOrdering =
        HeaderTemplate.append(HeaderTemplate.create("hello", Collections.emptyList()),
            Arrays.asList("test 2", "test 1"));
    assertThat(new ArrayList<>(headerTemplateWithSecondOrdering.getValues()),
        equalTo(Arrays.asList("test 2", "test 1")));
  }

  @Test
  public void it_should_support_http_date() {
    HeaderTemplate headerTemplate =
        HeaderTemplate.create("Expires", Collections.singletonList("{expires}"));
    assertEquals("Wed, 4 Jul 2001 12:08:56 -0700",
        headerTemplate.expand(
            Collections.singletonMap("expires", "Wed, 4 Jul 2001 12:08:56 -0700")));
  }

  @Test
  public void it_should_support_json_literal_values() {
    HeaderTemplate headerTemplate =
        HeaderTemplate.create("CustomHeader", Collections.singletonList("{jsonParam}"));

    assertEquals("{\"string\": \"val\", \"string2\": \"this should not be truncated\"}",
        headerTemplate.expand(
            Collections.singletonMap(
                "jsonParam",
                "{\"string\": \"val\", \"string2\": \"this should not be truncated\"}")));

  }
}
