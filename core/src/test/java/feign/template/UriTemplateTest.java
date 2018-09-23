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
import static org.assertj.core.api.Assertions.fail;
import feign.Util;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class UriTemplateTest {

  @Test
  public void emptyRelativeTemplate() {
    String template = "/";
    UriTemplate uriTemplate = UriTemplate.create(template, Util.UTF_8);
    assertThat(uriTemplate.expand(Collections.emptyMap())).isEqualToIgnoringCase("/");
  }

  @Test(expected = IllegalArgumentException.class)
  public void nullTemplate() {
    UriTemplate.create(null, Util.UTF_8);
  }

  @Test
  public void emptyTemplate() {
    UriTemplate.create("", Util.UTF_8);
  }

  @Test
  public void simpleTemplate() {
    String template = "https://www.example.com/foo/{bar}";
    UriTemplate uriTemplate = UriTemplate.create(template, Util.UTF_8);

    /* verify that the template has 1 variables names foo */
    List<String> uriTemplateVariables = uriTemplate.getVariables();
    assertThat(uriTemplateVariables).contains("bar").hasSize(1);

    /* expand the template */
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("bar", "bar");
    String expandedTemplate = uriTemplate.expand(variables);
    assertThat(expandedTemplate).isEqualToIgnoringCase("https://www.example.com/foo/bar");
    assertThat(URI.create(expandedTemplate)).isNotNull();
  }

  @Test
  public void simpleTemplateMultipleExpressions() {
    String template = "https://www.example.com/{foo}/{bar}/details";
    UriTemplate uriTemplate = UriTemplate.create(template, Util.UTF_8);

    /* verify that the template has 2 variables names foo and bar */
    List<String> uriTemplateVariables = uriTemplate.getVariables();
    assertThat(uriTemplateVariables).contains("foo", "bar").hasSize(2);

    /* expand the template */
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("foo", "first");
    variables.put("bar", "second");
    String expandedTemplate = uriTemplate.expand(variables);
    assertThat(expandedTemplate)
        .isEqualToIgnoringCase("https://www.example.com/first/second/details");
    assertThat(URI.create(expandedTemplate)).isNotNull();
  }

  @Test
  public void simpleTemplateMultipleSequentialExpressions() {
    String template = "https://www.example.com/{foo}{bar}/{baz}/details";
    UriTemplate uriTemplate = UriTemplate.create(template, Util.UTF_8);

    /* verify that the template has 2 variables names foo and bar */
    List<String> uriTemplateVariables = uriTemplate.getVariables();
    assertThat(uriTemplateVariables).contains("foo", "bar", "baz").hasSize(3);

    /* expand the template */
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("foo", "first");
    variables.put("bar", "second");
    variables.put("baz", "third");
    String expandedTemplate = uriTemplate.expand(variables);
    assertThat(expandedTemplate)
        .isEqualToIgnoringCase("https://www.example.com/firstsecond/third/details");
    assertThat(URI.create(expandedTemplate)).isNotNull();
  }

  @Test
  public void simpleTemplateUnresolvedVariablesAreRemoved() {
    String template = "https://www.example.com/{foo}?name={name}";
    UriTemplate uriTemplate = UriTemplate.create(template, Util.UTF_8);
    assertThat(uriTemplate.getVariables()).contains("foo", "name").hasSize(2);

    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("name", "Albert");
    String expandedTemplate = uriTemplate.expand(variables);
    assertThat(expandedTemplate).isEqualToIgnoringCase("https://www.example.com/?name=Albert");
    assertThat(URI.create(expandedTemplate)).isNotNull();
  }

  @Test
  public void missingVariablesOnPathAreRemoved() {
    String template = "https://www.example.com/{foo}/items?name={name}";
    UriTemplate uriTemplate = UriTemplate.create(template, Util.UTF_8);
    assertThat(uriTemplate.getVariables()).contains("foo", "name").hasSize(2);

    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("name", "Albert");
    String expandedTemplate = uriTemplate.expand(variables);
    assertThat(expandedTemplate)
        .isEqualToIgnoringCase("https://www.example.com//items?name=Albert");
    assertThat(URI.create(expandedTemplate)).isNotNull();
  }

  @Test
  public void simpleTemplateWithRegularExpressions() {
    String template = "https://www.example.com/{foo:[0-9]{4}}/{bar}";
    UriTemplate uriTemplate = UriTemplate.create(template, Util.UTF_8);
    assertThat(uriTemplate.getVariables()).contains("foo", "bar").hasSize(2);

    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("foo", 1234);
    variables.put("bar", "stuff");
    String expandedTemplate = uriTemplate.expand(variables);
    assertThat(expandedTemplate).isEqualToIgnoringCase("https://www.example.com/1234/stuff");
    assertThat(URI.create(expandedTemplate)).isNotNull();
  }

  @Test(expected = IllegalArgumentException.class)
  public void simpleTemplateWithRegularExpressionsValidation() {
    String template = "https://www.example.com/{foo:[0-9]{4}}/{bar}";
    UriTemplate uriTemplate = UriTemplate.create(template, Util.UTF_8);
    assertThat(uriTemplate.getVariables()).contains("foo", "bar").hasSize(2);

    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("foo", "abcd");
    variables.put("bar", "stuff");

    /* the foo variable must be a number and no more than four, this should fail */
    uriTemplate.expand(variables);
    fail("Should not be able to expand, pattern does not match");
  }

  @Test
  public void nestedExpressionsAreLiterals() {
    /* the template of {foo{bar}}, will be treated as literals as nested templates are ignored */
    String template = "https://www.example.com/{foo{bar}}/{baz}";
    UriTemplate uriTemplate = UriTemplate.create(template, Util.UTF_8);
    assertThat(uriTemplate.getVariables()).contains("baz").hasSize(1);

    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("baz", "stuff");
    String expandedTemplate = uriTemplate.expand(variables);
    assertThat(expandedTemplate)
        .isEqualToIgnoringCase("https://www.example.com/%7Bfoo%7Bbar%7D%7D/stuff");
    assertThat(URI.create(expandedTemplate))
        .isNotNull(); // this should fail, the result is not a valid uri
  }

  @Test
  public void literalTemplate() {
    String template = "https://www.example.com/do/stuff";
    UriTemplate uriTemplate = UriTemplate.create(template, Util.UTF_8);
    String expandedTemplate = uriTemplate.expand(Collections.emptyMap());
    assertThat(expandedTemplate).isEqualToIgnoringCase(template);
    assertThat(URI.create(expandedTemplate)).isNotNull();
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectEmptyExpressions() {
    String template = "https://www.example.com/{}/things";
    UriTemplate.create(template, Util.UTF_8);
    fail("Should not accept empty expressions");
  }

  @Test
  public void testToString() {
    String template = "https://www.example.com/foo/{bar}/{baz:[0-9]}";
    UriTemplate uriTemplate = UriTemplate.create(template, Util.UTF_8);
    assertThat(uriTemplate.toString()).isEqualToIgnoringCase(template);
  }

  @Test
  public void encodeVariables() {
    String template = "https://www.example.com/{first}/{last}";
    UriTemplate uriTemplate = UriTemplate.create(template, Util.UTF_8);
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("first", "John Jacob");
    variables.put("last", "Jingleheimer Schmidt");
    String expandedTemplate = uriTemplate.expand(variables);
    assertThat(expandedTemplate)
        .isEqualToIgnoringCase("https://www.example.com/John%20Jacob/Jingleheimer%20Schmidt");
  }

  @Test
  public void encodeLiterals() {
    String template = "https://www.example.com/A Team";
    UriTemplate uriTemplate = UriTemplate.create(template, Util.UTF_8);
    String expandedTemplate = uriTemplate.expand(Collections.emptyMap());
    assertThat(expandedTemplate)
        .isEqualToIgnoringCase("https://www.example.com/A%20Team");
  }

  @Test
  public void ensurePlusIsSupportedOnPath() {
    String template = "https://www.example.com/sam+adams/beer/{type}";
    UriTemplate uriTemplate = UriTemplate.create(template, Util.UTF_8);
    String expanded = uriTemplate.expand(Collections.emptyMap());
    assertThat(expanded).isEqualToIgnoringCase("https://www.example.com/sam+adams/beer/");
  }

  @Test
  public void incompleteTemplateIsALiteral() {
    String template = "https://www.example.com/testing/foo}}";
    UriTemplate uriTemplate = UriTemplate.create(template, Util.UTF_8);
    assertThat(uriTemplate.expand(Collections.emptyMap()))
        .isEqualToIgnoringCase("https://www.example.com/testing/foo%7D%7D");
  }

  @Test(expected = IllegalArgumentException.class)
  public void substituteNullMap() {
    UriTemplate.create("stuff", Util.UTF_8).expand(null);
  }

  @Test
  public void skipAlreadyEncodedLiteral() {
    String template = "https://www.example.com/A%20Team";
    UriTemplate uriTemplate = UriTemplate.create(template, Util.UTF_8);
    String expandedTemplate = uriTemplate.expand(Collections.emptyMap());
    assertThat(expandedTemplate)
        .isEqualToIgnoringCase("https://www.example.com/A%20Team");
  }

  @Test
  public void skipAlreadyEncodedVariable() {
    String template = "https://www.example.com/testing/{foo}";
    UriTemplate uriTemplate = UriTemplate.create(template, Util.UTF_8);
    String encodedVariable = UriUtils.encode("Johnny Appleseed", Util.UTF_8);
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("foo", encodedVariable);
    assertThat(uriTemplate.expand(variables))
        .isEqualToIgnoringCase("https://www.example.com/testing/" + encodedVariable);
  }

  @Test
  public void skipSlashes() {
    String template = "https://www.example.com/{path}";
    UriTemplate uriTemplate = UriTemplate.create(template, false, Util.UTF_8);
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("path", "me/you/first");
    String encoded = uriTemplate.expand(variables);
    assertThat(encoded).isEqualToIgnoringCase("https://www.example.com/me/you/first");
  }

  @Test
  public void encodeSlashes() {
    String template = "https://www.example.com/{path}";
    UriTemplate uriTemplate = UriTemplate.create(template, Util.UTF_8);
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("path", "me/you/first");
    String encoded = uriTemplate.expand(variables);
    assertThat(encoded).isEqualToIgnoringCase("https://www.example.com/me%2Fyou%2Ffirst");
  }

  @Test
  public void testLiteralTemplateWithQueryString() {
    String template = "https://api.example.com?wsdl";
    UriTemplate uriTemplate = UriTemplate.create(template, Util.UTF_8);
    String expanded = uriTemplate.expand(Collections.emptyMap());
    assertThat(expanded).isEqualToIgnoringCase("https://api.example.com?wsdl");
  }
}
