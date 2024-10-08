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
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class QueryTemplateTest {

  @Test
  void templateToQueryString() {
    QueryTemplate template =
        QueryTemplate.create("name", Arrays.asList("Bob", "James", "Jason"), Util.UTF_8);
    assertThat(template.toString()).isEqualToIgnoringCase("name=Bob&name=James&name=Jason");
  }

  @Test
  void expandEmptyCollection() {
    QueryTemplate template =
        QueryTemplate.create("people", Collections.singletonList("{people}"), Util.UTF_8);
    String expanded = template.expand(Collections.singletonMap("people", Collections.emptyList()));
    assertThat(expanded).isEqualToIgnoringCase("people=");
  }

  @Test
  void expandCollection() {
    QueryTemplate template =
        QueryTemplate.create("people", Collections.singletonList("{people}"), Util.UTF_8);
    String expanded =
        template.expand(Collections.singletonMap("people", Arrays.asList("Bob", "James", "Jason")));
    assertThat(expanded).isEqualToIgnoringCase("people=Bob&people=James&people=Jason");
  }

  @Test
  void expandCollectionWithBlanks() {
    QueryTemplate template =
        QueryTemplate.create("people", Collections.singletonList("{people}"), Util.UTF_8);
    String expanded =
        template.expand(Collections.singletonMap("people", Arrays.asList("", "Jason", "James")));
    assertThat(expanded).isEqualToIgnoringCase("people=&people=Jason&people=James");
  }

  @Test
  void expandSingleValue() {
    QueryTemplate template =
        QueryTemplate.create("name", Collections.singletonList("{value}"), Util.UTF_8);
    String expanded = template.expand(Collections.singletonMap("value", "Magnum P.I."));
    assertThat(expanded).isEqualToIgnoringCase("name=Magnum%20P.I.");
  }

  @Test
  void expandMultipleValues() {
    QueryTemplate template =
        QueryTemplate.create("name", Arrays.asList("Bob", "James", "Jason"), Util.UTF_8);
    String expanded = template.expand(Collections.emptyMap());
    assertThat(expanded).isEqualToIgnoringCase("name=Bob&name=James&name=Jason");
  }

  @Test
  void unresolvedQuery() {
    QueryTemplate template =
        QueryTemplate.create("name", Collections.singletonList("{value}"), Util.UTF_8);
    String expanded = template.expand(Collections.emptyMap());
    assertThat(expanded).isNullOrEmpty();
  }

  @Test
  void unresolvedMultiValueQueryTemplates() {
    QueryTemplate template =
        QueryTemplate.create("name", Arrays.asList("{bob}", "{james}", "{jason}"), Util.UTF_8);
    String expanded = template.expand(Collections.emptyMap());
    assertThat(expanded).isNullOrEmpty();
  }

  @Test
  void explicitNullValuesAreRemoved() {
    QueryTemplate template =
        QueryTemplate.create("name", Collections.singletonList("{value}"), Util.UTF_8);
    String expanded = template.expand(Collections.singletonMap("value", null));
    assertThat(expanded).isNullOrEmpty();
  }

  @Test
  void emptyParameterRemains() {
    QueryTemplate template =
        QueryTemplate.create("name", Collections.singletonList("{value}"), Util.UTF_8);
    String expanded = template.expand(Collections.singletonMap("value", ""));
    assertThat(expanded).isEqualToIgnoringCase("name=");
  }

  @Test
  void collectionFormat() {
    QueryTemplate template =
        QueryTemplate.create(
            "name", Arrays.asList("James", "Jason"), Util.UTF_8, CollectionFormat.CSV);
    String expanded = template.expand(Collections.emptyMap());
    assertThat(expanded).isEqualToIgnoringCase("name=James%2CJason");
  }

  @Test
  void expandName() {
    QueryTemplate template =
        QueryTemplate.create("{name}", Arrays.asList("James", "Jason"), Util.UTF_8);
    String expanded = template.expand(Collections.singletonMap("name", "firsts"));
    assertThat(expanded).isEqualToIgnoringCase("firsts=James&firsts=Jason");
  }

  @Test
  void expandPureParameter() {
    QueryTemplate template = QueryTemplate.create("{name}", Collections.emptyList(), Util.UTF_8);
    String expanded = template.expand(Collections.singletonMap("name", "firsts"));
    assertThat(expanded).isEqualToIgnoringCase("firsts");
  }

  @Test
  void expandPureParameterWithSlash() {
    QueryTemplate template =
        QueryTemplate.create("/path/{name}", Collections.emptyList(), Util.UTF_8);
    String expanded = template.expand(Collections.singletonMap("name", "firsts"));
    assertThat(expanded).isEqualToIgnoringCase("/path/firsts");
  }

  @Test
  void expandNameUnresolved() {
    QueryTemplate template =
        QueryTemplate.create("{parameter}", Arrays.asList("James", "Jason"), Util.UTF_8);
    String expanded = template.expand(Collections.singletonMap("name", "firsts"));
    assertThat(expanded).isEqualToIgnoringCase("%7Bparameter%7D=James&%7Bparameter%7D=Jason");
  }

  @Test
  void expandSingleValueWithComma() {
    QueryTemplate template =
        QueryTemplate.create("collection", Collections.singletonList("{collection}"), Util.UTF_8);
    String expanded = template.expand(Collections.singletonMap("collection", "one,two,three"));
    assertThat(expanded).isEqualToIgnoringCase("collection=one%2Ctwo%2Cthree");
  }

  @Test
  void expandSingleValueWithPipe() {
    QueryTemplate template =
        QueryTemplate.create("collection", Collections.singletonList("{collection}"), Util.UTF_8);
    String expanded = template.expand(Collections.singletonMap("collection", "one|two|three"));
    assertThat(expanded).isEqualToIgnoringCase("collection=one%7Ctwo%7Cthree");
  }

  @Test
  void expandSingleValueWithSpace() {
    QueryTemplate template =
        QueryTemplate.create("collection", Collections.singletonList("{collection}"), Util.UTF_8);
    String expanded = template.expand(Collections.singletonMap("collection", "one two three"));
    assertThat(expanded).isEqualToIgnoringCase("collection=one%20two%20three");
  }

  @Test
  void expandSingleValueWithTab() {
    QueryTemplate template =
        QueryTemplate.create("collection", Collections.singletonList("{collection}"), Util.UTF_8);
    String expanded = template.expand(Collections.singletonMap("collection", "one\ttwo\tthree"));
    assertThat(expanded).isEqualToIgnoringCase("collection=one%09two%09three");
  }

  @Test
  void expandSingleValueWithJson() {
    QueryTemplate template =
        QueryTemplate.create("json", Collections.singletonList("{json}"), Util.UTF_8);
    String expanded =
        template.expand(
            Collections.singletonMap("json", "{\"name\":\"feign\",\"version\": \"10\"}"));
    assertThat(expanded)
        .isEqualToIgnoringCase("json=%7B%22name%22%3A%22feign%22%2C%22version%22%3A%20%2210%22%7D");
  }

  @Test
  void expandCollectionValueWithBrackets() {
    QueryTemplate template =
        QueryTemplate.create(
            "collection[]",
            Collections.singletonList("{collection[]}"),
            Util.UTF_8,
            CollectionFormat.CSV);
    String expanded =
        template.expand(Collections.singletonMap("collection[]", Arrays.asList("1", "2")));
    /* brackets will be pct-encoded */
    assertThat(expanded).isEqualToIgnoringCase("collection%5B%5D=1%2C2");
  }

  @Test
  void expandCollectionValueWithDollar() {
    QueryTemplate template =
        QueryTemplate.create(
            "$collection",
            Collections.singletonList("{$collection}"),
            Util.UTF_8,
            CollectionFormat.CSV);
    String expanded =
        template.expand(Collections.singletonMap("$collection", Arrays.asList("1", "2")));
    /* dollar will be pct-encoded */
    assertThat(expanded).isEqualToIgnoringCase("%24collection=1%2C2");
  }
}
