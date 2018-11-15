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
package feign.template;


import static org.assertj.core.api.Assertions.assertThat;
import feign.CollectionFormat;
import feign.Util;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class QueryTemplateTest {

  @Test
  public void templateToQueryString() {
    QueryTemplate template =
        QueryTemplate.create("name", Arrays.asList("Bob", "James", "Jason"), Util.UTF_8);
    assertThat(template.toString()).isEqualToIgnoringCase("name=Bob&name=James&name=Jason");
  }

  @Test
  public void expandSingleValue() {
    QueryTemplate template =
        QueryTemplate.create("name", Collections.singletonList("{value}"), Util.UTF_8);
    String expanded = template.expand(Collections.singletonMap("value", "Magnum P.I."));
    assertThat(expanded).isEqualToIgnoringCase("name=Magnum%20P.I.");
  }

  @Test
  public void expandMultipleValues() {
    QueryTemplate template =
        QueryTemplate.create("name", Arrays.asList("Bob", "James", "Jason"), Util.UTF_8);
    String expanded = template.expand(Collections.emptyMap());
    assertThat(expanded).isEqualToIgnoringCase("name=Bob&name=James&name=Jason");
  }

  @Test
  public void unresolvedQuery() {
    QueryTemplate template =
        QueryTemplate.create("name", Collections.singletonList("{value}"), Util.UTF_8);
    String expanded = template.expand(Collections.emptyMap());
    assertThat(expanded).isNullOrEmpty();
  }

  @Test
  public void unresolvedMultiValueQueryTemplates() {
    QueryTemplate template =
        QueryTemplate.create("name", Arrays.asList("{bob}", "{james}", "{jason}"), Util.UTF_8);
    String expanded = template.expand(Collections.emptyMap());
    assertThat(expanded).isNullOrEmpty();
  }

  @Test
  public void collectionFormat() {
    QueryTemplate template =
        QueryTemplate
            .create("name", Arrays.asList("James", "Jason"), Util.UTF_8, CollectionFormat.CSV);
    String expanded = template.expand(Collections.emptyMap());
    assertThat(expanded).isEqualToIgnoringCase("name=James,Jason");
  }

  @Test
  public void expandName() {
    QueryTemplate template =
        QueryTemplate.create("{name}", Arrays.asList("James", "Jason"), Util.UTF_8);
    String expanded = template.expand(Collections.singletonMap("name", "firsts"));
    assertThat(expanded).isEqualToIgnoringCase("firsts=James&firsts=Jason");
  }

  @Test
  public void expandPureParameter() {
    QueryTemplate template =
        QueryTemplate.create("{name}", Collections.emptyList(), Util.UTF_8);
    String expanded = template.expand(Collections.singletonMap("name", "firsts"));
    assertThat(expanded).isEqualToIgnoringCase("firsts");
  }

  @Test
  public void expandPureParameterWithSlash() {
    QueryTemplate template =
        QueryTemplate.create("/path/{name}", Collections.emptyList(), Util.UTF_8);
    String expanded = template.expand(Collections.singletonMap("name", "firsts"));
    assertThat(expanded).isEqualToIgnoringCase("/path/firsts");
  }

  @Test
  public void expandNameUnresolved() {
    QueryTemplate template =
        QueryTemplate.create("{parameter}", Arrays.asList("James", "Jason"), Util.UTF_8);
    String expanded = template.expand(Collections.singletonMap("name", "firsts"));
    assertThat(expanded).isEqualToIgnoringCase("%7Bparameter%7D=James&%7Bparameter%7D=Jason");
  }
}
