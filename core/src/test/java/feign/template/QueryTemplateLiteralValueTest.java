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
package feign.template;

import static org.assertj.core.api.Assertions.assertThat;

import feign.CollectionFormat;
import feign.Util;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for query values that contain a comma.
 *
 * <p>A comma inside a literal value (a {@code @RequestLine} such as {@code GET /items?ids=1,2}, or
 * {@link feign.RequestTemplate#query(String, Iterable)}) is part of that single value. Only the
 * expansion of a collection variable is joined with a comma and split back into repeated values.
 * Both a raw {@code ,} and the equivalent {@code %2C} are accepted, they denote the same value.
 */
class QueryTemplateLiteralValueTest {

  @Test
  void literalValueWithCommaIsOneValue() {
    QueryTemplate template =
        QueryTemplate.create("ids", Collections.singletonList("1,2"), Util.UTF_8);

    assertThat(valuesOf(template.expand(Collections.emptyMap()), "ids")).containsExactly("1,2");
  }

  @Test
  void literalValueWithCommaKeepsDeclaredCollectionFormat() {
    QueryTemplate template =
        QueryTemplate.create(
            "ids", Collections.singletonList("1,2"), Util.UTF_8, CollectionFormat.CSV);

    assertThat(valuesOf(template.expand(Collections.emptyMap()), "ids")).containsExactly("1,2");
  }

  @Test
  void literalValueWithCommaIsNotExpandedIntoRepeatedNames() {
    QueryTemplate template =
        QueryTemplate.create("sort", Collections.singletonList("name,age"), Util.UTF_8);

    assertThat(template.expand(Collections.emptyMap())).doesNotContain("sort=name&sort=age");
    assertThat(valuesOf(template.expand(Collections.emptyMap()), "sort"))
        .containsExactly("name,age");
  }

  @Test
  void scalarVariableValueWithCommaIsOneValue() {
    QueryTemplate template =
        QueryTemplate.create("ids", Collections.singletonList("{ids}"), Util.UTF_8);

    assertThat(valuesOf(template.expand(Collections.singletonMap("ids", "1,2")), "ids"))
        .containsExactly("1,2");
  }

  @Test
  void literalPrefixAndSuffixAroundScalarVariable() {
    QueryTemplate template =
        QueryTemplate.create("ids", Collections.singletonList("v1-{ids}-end"), Util.UTF_8);

    assertThat(valuesOf(template.expand(Collections.singletonMap("ids", "1,2")), "ids"))
        .containsExactly("v1-1,2-end");
  }

  @Test
  void literalCommasNextToScalarVariableAreNotSeparators() {
    QueryTemplate template =
        QueryTemplate.create("ids", Collections.singletonList("1,2{ids}"), Util.UTF_8);

    assertThat(valuesOf(template.expand(Collections.singletonMap("ids", "3")), "ids"))
        .containsExactly("1,23");
  }

  @Test
  void collectionElementWithCommaIsOneValue() {
    QueryTemplate template =
        QueryTemplate.create("names", Collections.singletonList("{names}"), Util.UTF_8);

    assertThat(
            valuesOf(
                template.expand(Collections.singletonMap("names", Arrays.asList("a,b", "c"))),
                "names"))
        .containsExactly("a,b", "c");
  }

  @Test
  void arrayVariableIsExpandedLikeAList() {
    QueryTemplate template =
        QueryTemplate.create("names", Collections.singletonList("{names}"), Util.UTF_8);

    assertThat(
            valuesOf(
                template.expand(Collections.singletonMap("names", new String[] {"a,b", "c"})),
                "names"))
        .containsExactly("a,b", "c");
  }

  /** Control: a scalar with a comma is pct-encoded, as it has always been. */
  @Test
  void scalarVariableValueWithCommaIsEncoded() {
    QueryTemplate template =
        QueryTemplate.create("collection", Collections.singletonList("{collection}"), Util.UTF_8);

    assertThat(template.expand(Collections.singletonMap("collection", "one,two,three")))
        .isEqualTo("collection=one%2Ctwo%2Cthree");
  }

  /** Control: collection variables are still expanded into repeated (exploded) parameters. */
  @Test
  void collectionVariableIsStillExpandedToRepeatedParameters() {
    QueryTemplate template =
        QueryTemplate.create("name", Collections.singletonList("{names}"), Util.UTF_8);

    assertThat(template.expand(Collections.singletonMap("names", Arrays.asList("James", "Jason"))))
        .isEqualTo("name=James&name=Jason");
  }

  /** Control: the CSV collection format still joins multiple values with an encoded comma. */
  @Test
  void csvCollectionFormatStillJoinsValues() {
    QueryTemplate template =
        QueryTemplate.create(
            "name", Arrays.asList("James", "Jason"), Util.UTF_8, CollectionFormat.CSV);

    assertThat(valuesOf(template.expand(Collections.emptyMap()), "name"))
        .containsExactly("James,Jason");
  }

  /** Values of the named parameter, decoded, so that {@code ,} and {@code %2C} compare equal. */
  private static List<String> valuesOf(String expanded, String name) {
    List<String> values = new ArrayList<>();
    for (String parameter : expanded.split("&")) {
      int separator = parameter.indexOf('=');
      String rawName = separator < 0 ? parameter : parameter.substring(0, separator);
      String parameterName = UriUtils.decode(rawName, Util.UTF_8);
      if (name.equals(parameterName)) {
        values.add(
            separator < 0 ? null : UriUtils.decode(parameter.substring(separator + 1), Util.UTF_8));
      }
    }
    return values;
  }
}
