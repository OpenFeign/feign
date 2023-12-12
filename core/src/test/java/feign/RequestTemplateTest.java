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

import static feign.assertj.FeignAssertions.assertThat;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import feign.Request.HttpMethod;
import feign.template.UriUtils;

public class RequestTemplateTest {

  /**
   * Avoid depending on guava solely for map literals.
   */
  private static <K, V> Map<K, V> mapOf(K key, V val) {
    Map<K, V> result = new LinkedHashMap<>();
    result.put(key, val);
    return result;
  }

  private static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2) {
    Map<K, V> result = mapOf(k1, v1);
    result.put(k2, v2);
    return result;
  }

  private static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3) {
    Map<K, V> result = mapOf(k1, v1, k2, v2);
    result.put(k3, v3);
    return result;
  }

  private static String expand(String template, Map<String, Object> variables) {
    RequestTemplate requestTemplate = new RequestTemplate();
    requestTemplate.uri(template);
    return requestTemplate.resolve(variables).url();
  }

  @Test
  void expandUrlEncoded() {
    for (String val : Arrays.asList("apples", "sp ace", "unic???de", "qu?stion")) {
      assertThat(expand("/users/{user}", mapOf("user", val)))
          .isEqualTo("/users/" + UriUtils.encode(val, Util.UTF_8));
    }
  }

  @Test
  void expandMultipleParams() {
    assertThat(expand("/users/{user}/{repo}", mapOf("user", "unic???de", "repo", "foo")))
        .isEqualTo("/users/unic%3F%3F%3Fde/foo");
  }

  @Test
  void expandParamKeyHyphen() {
    assertThat(expand("/{user-dir}", mapOf("user-dir", "foo"))).isEqualTo("/foo");
  }

  @Test
  void expandMissingParamProceeds() {
    assertThat(expand("/{user-dir}", mapOf("user_dir", "foo"))).isEqualTo("/");
  }

  @Test
  void resolveTemplateWithParameterizedPathSkipsEncodingSlash() {
    RequestTemplate template = new RequestTemplate().method(HttpMethod.GET).uri("{zoneId}");

    template = template.resolve(mapOf("zoneId", "/hostedzone/Z1PA6795UKMFR9"));

    assertThat(template).hasUrl("/hostedzone/Z1PA6795UKMFR9");
  }

  @Test
  void resolveTemplateWithBinaryBody() {
    RequestTemplate template = new RequestTemplate().method(HttpMethod.GET).uri("{zoneId}")
        .body(new byte[] {7, 3, -3, -7}, null);
    template = template.resolve(mapOf("zoneId", "/hostedzone/Z1PA6795UKMFR9"));

    assertThat(template).hasUrl("/hostedzone/Z1PA6795UKMFR9");
  }

  @Test
  void canInsertAbsoluteHref() {
    RequestTemplate template =
        new RequestTemplate().method(HttpMethod.GET).uri("/hostedzone/Z1PA6795UKMFR9");

    template.target("https://route53.amazonaws.com/2012-12-12");

    assertThat(template)
        .hasUrl("https://route53.amazonaws.com/2012-12-12/hostedzone/Z1PA6795UKMFR9");
  }

  @Test
  void resolveTemplateWithRelativeUriWithQuery() {
    RequestTemplate template =
        new RequestTemplate().method(HttpMethod.GET).uri("/wsdl/testcase?wsdl")
            .target("https://api.example.com");

    assertThat(template).hasUrl("https://api.example.com/wsdl/testcase?wsdl");
  }

  @Test
  void resolveTemplateWithBaseAndParameterizedQuery() {
    RequestTemplate template =
        new RequestTemplate().method(HttpMethod.GET).uri("/?Action=DescribeRegions")
            .query("RegionName.1", "{region}");

    template = template.resolve(mapOf("region", "eu-west-1"));

    assertThat(template).hasQueries(entry("Action", Collections.singletonList("DescribeRegions")),
        entry("RegionName.1", Collections.singletonList("eu-west-1")));
  }

  @Test
  void resolveTemplateWithBaseAndParameterizedIterableQuery() {
    RequestTemplate template =
        new RequestTemplate().method(HttpMethod.GET).uri("/?Query=one").query("Queries",
            "{queries}");

    template = template.resolve(mapOf("queries", Arrays.asList("us-east-1", "eu-west-1")));

    assertThat(template).hasQueries(entry("Query", Collections.singletonList("one")),
        entry("Queries", asList("us-east-1", "eu-west-1")));
  }

  @Test
  void resolveTemplateWithMixedCollectionFormatsByQuery() {
    RequestTemplate template = new RequestTemplate().method(HttpMethod.GET)
        .collectionFormat(CollectionFormat.EXPLODED).uri("/api/collections").query("keys", "{keys}") // default
        // collection
        // format
        .query("values[]", Collections.singletonList("{values[]}"), CollectionFormat.CSV);

    template = template
        .resolve(mapOf("keys", Arrays.asList("one", "two"), "values[]", Arrays.asList("1", "2")));

    assertThat(template.url())
        .isEqualToIgnoringCase("/api/collections?keys=one&keys=two&values%5B%5D=1%2C2");
  }

  @Test
  void resolveTemplateWithHeaderSubstitutions() {
    RequestTemplate template =
        new RequestTemplate().method(HttpMethod.GET).header("Auth-Token", "{authToken}");

    template = template.resolve(mapOf("authToken", "1234"));

    assertThat(template).hasHeaders(entry("Auth-Token", Collections.singletonList("1234")));
  }

  @Test
  void resolveTemplateWithHeaderSubstitutionsNotAtStart() {
    RequestTemplate template = new RequestTemplate().method(HttpMethod.GET).header("Authorization",
        "Bearer {token}");

    template = template.resolve(mapOf("token", "1234"));

    assertThat(template)
        .hasHeaders(entry("Authorization", Collections.singletonList("Bearer 1234")));
  }

  @Test
  void resolveTemplateWithHeaderWithEscapedCurlyBrace() {
    RequestTemplate template = new RequestTemplate().method(HttpMethod.GET).header("Encoded",
        "{{{{dont_expand_me}}");

    template.resolve(mapOf("dont_expand_me", "1234"));

    assertThat(template)
        .hasHeaders(entry("Encoded", Collections.singletonList("{{{{dont_expand_me}}")));
  }

  @Test
  void resolveTemplateWithHeaderContainingJsonLiteral() {
    String json = "{\"A\":{\"B\":\"C\"}}";
    RequestTemplate template =
        new RequestTemplate().method(HttpMethod.GET).header("A-Header", json);

    template.resolve(new LinkedHashMap<>());
    assertThat(template).hasHeaders(entry("A-Header", Collections.singletonList(json)));
  }

  @Test
  void resolveTemplateWithHeaderWithJson() {
    String json = "{ \"string\": \"val\", \"string2\": \"this should not be truncated\"}";
    RequestTemplate template =
        new RequestTemplate().method(HttpMethod.GET).header("A-Header", "{aHeader}");

    template = template.resolve(mapOf("aHeader", json));

    assertThat(template).hasHeaders(entry("A-Header", Collections.singletonList(json)));
  }

  @Test
  void resolveTemplateWithHeaderWithNestedJson() {
    String json =
        "{ \"string\": \"val\", \"string2\": \"this should not be truncated\", \"property\": {\"nested\": true}}";
    RequestTemplate template =
        new RequestTemplate().method(HttpMethod.GET).header("A-Header", "{aHeader}");

    template = template.resolve(mapOf("aHeader", json));

    assertThat(template).hasHeaders(entry("A-Header", Collections.singletonList(json)));
  }

  /**
   * This ensures we don't mess up vnd types
   */
  @Test
  void resolveTemplateWithHeaderIncludingSpecialCharacters() {
    RequestTemplate template = new RequestTemplate().method(HttpMethod.GET).header("Accept",
        "application/vnd.github.v3+{type}");

    template = template.resolve(mapOf("type", "json"));

    assertThat(template)
        .hasHeaders(entry("Accept", Collections.singletonList("application/vnd.github.v3+json")));
  }

  @Test
  void resolveTemplateWithHeaderEmptyResult() {
    RequestTemplate template =
        new RequestTemplate().method(HttpMethod.GET).header("Encoded", "{var}");

    template = template.resolve(mapOf("var", ""));

    assertThat(template).hasNoHeader("Encoded");
  }

  @Test
  void resolveTemplateWithMixedRequestLineParams() {
    RequestTemplate template = new RequestTemplate().method(HttpMethod.GET)//
        .uri("/domains/{domainId}/records")//
        .query("name", "{name}")//
        .query("type", "{type}");

    template = template.resolve(mapOf("domainId", 1001, "name", "denominator.io", "type", "CNAME"));

    assertThat(template).hasQueries(entry("name", Collections.singletonList("denominator.io")),
        entry("type", Collections.singletonList("CNAME")));
  }

  @Test
  void insertHasQueryParams() {
    RequestTemplate template = new RequestTemplate().method(HttpMethod.GET)//
        .uri("/domains/1001/records")//
        .query("name", "denominator.io")//
        .query("type", "CNAME");

    template.target("https://host/v1.0/1234?provider=foo");

    assertThat(template).hasPath("https://host/v1.0/1234/domains/1001/records").hasQueries(
        entry("name", Collections.singletonList("denominator.io")),
        entry("type", Collections.singletonList("CNAME")),
        entry("provider", Collections.singletonList("foo")));
  }

  @Test
  void resolveTemplateWithBodyTemplateSetsBodyAndContentLength() {
    RequestTemplate template = new RequestTemplate().method(HttpMethod.POST)
        .bodyTemplate("%7B\"customer_name\": \"{customer_name}\", \"user_name\": \"{user_name}\", "
            + "\"password\": \"{password}\"%7D", Util.UTF_8);

    template = template
        .resolve(
            mapOf("customer_name", "netflix", "user_name", "denominator", "password", "password"));

    assertThat(template)
        .hasBody(
            "{\"customer_name\": \"netflix\", \"user_name\": \"denominator\", \"password\": \"password\"}")
        .hasHeaders(entry("Content-Length",
            Collections.singletonList(String.valueOf(template.body().length))));
  }

  @Test
  void resolveTemplateWithBodyTemplateDoesNotDoubleDecode() {
    RequestTemplate template = new RequestTemplate().method(HttpMethod.POST).bodyTemplate(
        "%7B\"customer_name\": \"{customer_name}\", \"user_name\": \"{user_name}\", \"password\": \"{password}\"%7D",
        Util.UTF_8);

    template = template
        .resolve(mapOf("customer_name", "netflix", "user_name", "denominator", "password",
            "abc+123%25d8"));

    assertThat(template).hasBody(
        "{\"customer_name\": \"netflix\", \"user_name\": \"denominator\", \"password\": \"abc+123%25d8\"}");
  }

  @Test
  void skipUnresolvedQueries() {
    RequestTemplate template =
        new RequestTemplate().method(HttpMethod.GET).uri("/domains/{domainId}/records")//
            .query("optional", "{optional}")//
            .query("name", "{nameVariable}");

    template = template.resolve(mapOf("domainId", 1001, "nameVariable", "denominator.io"));

    assertThat(template).hasQueries(entry("name", Collections.singletonList("denominator.io")));
  }

  @Test
  void allQueriesUnresolvable() {
    RequestTemplate template = new RequestTemplate().method(HttpMethod.GET)//
        .uri("/domains/{domainId}/records")//
        .query("optional", "{optional}")//
        .query("optional2", "{optional2}");

    template = template.resolve(mapOf("domainId", 1001));

    assertThat(template).hasUrl("/domains/1001/records").hasQueries();
  }

  @Test
  void spaceEncodingInUrlParam() {
    RequestTemplate template = new RequestTemplate().method(HttpMethod.GET)//
        .uri("/api/{value1}?key={value2}");

    template = template.resolve(mapOf("value1", "ABC 123", "value2", "XYZ 123"));

    assertThat(template.request().url()).isEqualTo("/api/ABC%20123?key=XYZ%20123");
  }

  @Test
  void useCaseInsensitiveHeaderFieldNames() {
    final RequestTemplate template = new RequestTemplate();

    final String value = "value1";
    template.header("TEST", value);

    final String value2 = "value2";
    template.header("tEST", value2);

    final Collection<String> test = template.headers().get("test");

    final String assertionMessage = "Header field names should be case insensitive";

    assertThat(test).as(assertionMessage).isNotNull();
    assertThat(test.contains(value)).as(assertionMessage).isTrue();
    assertThat(test.contains(value2)).as(assertionMessage).isTrue();
    assertThat(template.headers()).hasSize(1);
    assertThat(template.headers().get("tesT")).hasSize(2);
  }

  @Test
  void encodeSlashTest() {
    RequestTemplate template =
        new RequestTemplate().method(HttpMethod.GET).uri("/api/{vhost}").decodeSlash(false);

    template = template.resolve(mapOf("vhost", "/"));

    assertThat(template).hasUrl("/api/%2F");
  }

  /**
   * Implementations have a bug if they pass junk as the http method.
   */
  @SuppressWarnings("deprecation")
  @Test
  void uriStuffedIntoMethod() {
    Throwable exception = assertThrows(IllegalArgumentException.class,
        () -> new RequestTemplate().method("/path?queryParam={queryParam}"));
    assertThat(exception.getMessage())
        .contains("Invalid HTTP Method: /path?queryParam={queryParam}");
  }

  @Test
  void encodedQueryClearedOnNull() {
    RequestTemplate template = new RequestTemplate();

    template.query("param[]", "value");
    assertThat(template).hasQueries(entry("param[]", Collections.singletonList("value")));

    template.query("param[]", (String[]) null);
    assertThat(template.queries()).isEmpty();
  }

  @Test
  void encodedQuery() {
    RequestTemplate template = new RequestTemplate().query("params[]", "foo%20bar");
    assertThat(template.queryLine()).isEqualTo("?params%5B%5D=foo%20bar");
    assertThat(template).hasQueries(entry("params[]", Collections.singletonList("foo%20bar")));
  }

  @Test
  void encodedQueryWithUnsafeCharactersMixedWithUnencoded() {
    RequestTemplate template = new RequestTemplate().query("params[]", "not encoded") // stored as
                                                                                      // "param%5D%5B"
        .query("params[]", "encoded"); // stored as "param[]"

    assertThat(template.queryLine()).isEqualTo("?params%5B%5D=not%20encoded&params%5B%5D=encoded");
    Map<String, Collection<String>> queries = template.queries();
    assertThat(queries).containsKey("params[]");
    assertThat(queries.get("params[]")).contains("encoded").contains("not%20encoded");
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldRetrieveHeadersWithoutNull() {
    RequestTemplate template = new RequestTemplate().header("key1", (String) null)
        .header("key2", Collections.emptyList()).header("key3", (Collection) null)
        .header("key4", "valid")
        .header("key5", "valid").header("key6", "valid").header("key7", "valid");

    assertThat(template.headers()).hasSize(4);
    assertThat(template.headers().keySet()).containsExactly("key4", "key5", "key6", "key7");

  }

  public void shouldNotMutateInternalHeadersMap() {
    RequestTemplate template = new RequestTemplate().header("key1", "valid");

    assertThat(template.headers()).hasSize(1);
    assertThat(template.headers().keySet()).containsExactly("key1");
    assertThat(template.headers().get("key1")).containsExactly("valid");

    template.headers().put("key2", Collections.singletonList("other value"));
    // nothing should change
    assertThat(template.headers()).hasSize(1);
    assertThat(template.headers().keySet()).containsExactly("key1");
    assertThat(template.headers().get("key1")).containsExactly("valid");

    template.headers().get("key1").add("value2");
    // nothing should change either
    assertThat(template.headers()).hasSize(1);
    assertThat(template.headers().keySet()).containsExactly("key1");
    assertThat(template.headers().get("key1")).containsExactly("valid");
  }

  @Test
  void fragmentShouldNotBeEncodedInUri() {
    RequestTemplate template = new RequestTemplate().method(HttpMethod.GET).uri("/path#fragment")
        .queries(mapOf("key1", Collections.singletonList("value1")));

    assertThat(template.url()).isEqualTo("/path?key1=value1#fragment");
  }

  @Test
  void fragmentShouldNotBeEncodedInTarget() {
    RequestTemplate template = new RequestTemplate().method(HttpMethod.GET)
        .target("https://example.com/path#fragment")
        .queries(mapOf("key1", Collections.singletonList("value1")));

    assertThat(template.url()).isEqualTo("https://example.com/path?key1=value1#fragment");
  }

  @Test
  void urlEncodingRemainsInPlace() {
    RequestTemplate template = new RequestTemplate().method(HttpMethod.GET)
        .target("https://exa%23mple.com/path%7Cpath");

    assertThat(template.url()).isEqualTo("https://exa%23mple.com/path%7Cpath");
  }

  @Test
  void slashShouldNotBeAppendedForMatrixParams() {
    RequestTemplate template =
        new RequestTemplate().method(HttpMethod.GET).uri("/path;key1=value1;key2=value2",
            true);

    assertThat(template.url()).isEqualTo("/path;key1=value1;key2=value2");
  }

  @Test
  void encodedReservedPreserveSlash() {
    RequestTemplate template = new RequestTemplate();
    template.uri("/get?url={url}");
    template.method(HttpMethod.GET);
    template = template.resolve(Collections.singletonMap("url", "https://www.google.com"));
    assertThat(template.url()).isEqualToIgnoringCase("/get?url=https%3A//www.google.com");
  }

  @Test
  void encodedReservedEncodeSlash() {
    RequestTemplate template = new RequestTemplate();
    template.uri("/get?url={url}");
    template.decodeSlash(false);
    template.method(HttpMethod.GET);
    template = template.resolve(Collections.singletonMap("url", "https://www.google.com"));
    assertThat(template.url()).isEqualToIgnoringCase("/get?url=https%3A%2F%2Fwww.google.com");
  }
}
