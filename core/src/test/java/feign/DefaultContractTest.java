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
package feign;

import static feign.assertj.FeignAssertions.assertThat;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.reflect.TypeToken;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import org.assertj.core.api.Fail;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Tests interfaces defined per {@link Contract.Default} are interpreted into expected {@link feign
 * .RequestTemplate template} instances.
 */
class DefaultContractTest {

  Contract.Default contract = new Contract.Default();

  @Test
  void httpMethods() throws Exception {
    assertThat(parseAndValidateMetadata(Methods.class, "post").template()).hasMethod("POST");

    assertThat(parseAndValidateMetadata(Methods.class, "put").template()).hasMethod("PUT");

    assertThat(parseAndValidateMetadata(Methods.class, "get").template()).hasMethod("GET");

    assertThat(parseAndValidateMetadata(Methods.class, "delete").template()).hasMethod("DELETE");
  }

  @Test
  void bodyParamIsGeneric() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(BodyParams.class, "post", List.class);

    assertThat(md.bodyIndex()).isEqualTo(0);
    assertThat(md.bodyType()).isEqualTo(new TypeToken<List<String>>() {}.getType());
  }

  @Test
  void bodyParamWithPathParam() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(BodyParams.class, "post", int.class, List.class);

    assertThat(md.bodyIndex()).isEqualTo(1);
    assertThat(md.indexToName()).containsOnly(entry(0, asList("id")));
  }

  @Test
  void tooManyBodies() throws Exception {
    Throwable exception =
        assertThrows(
            IllegalStateException.class,
            () -> parseAndValidateMetadata(BodyParams.class, "tooMany", List.class, List.class));
    assertThat(exception.getMessage()).contains("Method has too many Body");
  }

  @Test
  void customMethodWithoutPath() throws Exception {
    assertThat(parseAndValidateMetadata(CustomMethod.class, "patch").template())
        .hasMethod("PATCH")
        .hasUrl("/");
  }

  @Test
  void queryParamsInPathExtract() throws Exception {
    assertThat(parseAndValidateMetadata(WithQueryParamsInPath.class, "none").template())
        .hasUrl("/")
        .hasQueries();

    assertThat(parseAndValidateMetadata(WithQueryParamsInPath.class, "one").template())
        .hasPath("/")
        .hasQueries(entry("Action", asList("GetUser")));

    assertThat(parseAndValidateMetadata(WithQueryParamsInPath.class, "two").template())
        .hasPath("/")
        .hasQueries(entry("Action", asList("GetUser")), entry("Version", asList("2010-05-08")));

    assertThat(parseAndValidateMetadata(WithQueryParamsInPath.class, "three").template())
        .hasPath("/")
        .hasQueries(
            entry("Action", asList("GetUser")),
            entry("Version", asList("2010-05-08")),
            entry("limit", asList("1")));

    assertThat(parseAndValidateMetadata(WithQueryParamsInPath.class, "twoAndOneEmpty").template())
        .hasPath("/")
        .hasQueries(
            entry("flag", new ArrayList<>()),
            entry("Action", asList("GetUser")),
            entry("Version", asList("2010-05-08")));

    assertThat(parseAndValidateMetadata(WithQueryParamsInPath.class, "oneEmpty").template())
        .hasPath("/")
        .hasQueries(entry("flag", new ArrayList<>()));

    assertThat(parseAndValidateMetadata(WithQueryParamsInPath.class, "twoEmpty").template())
        .hasPath("/")
        .hasQueries(entry("flag", new ArrayList<>()), entry("NoErrors", new ArrayList<>()));
  }

  @Test
  void bodyWithoutParameters() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(BodyWithoutParameters.class, "post");

    assertThat(md.template()).hasBody("<v01:getAccountsListOfUser/>");
  }

  @Test
  void headersOnMethodAddsContentTypeHeader() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(BodyWithoutParameters.class, "post");

    assertThat(md.template())
        .hasHeaders(
            entry("Content-Type", asList("application/xml")),
            entry("Content-Length", asList(String.valueOf(md.template().body().length))));
  }

  @Test
  void headersOnTypeAddsContentTypeHeader() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(HeadersOnType.class, "post");

    assertThat(md.template())
        .hasHeaders(
            entry("Content-Type", asList("application/xml")),
            entry("Content-Length", asList(String.valueOf(md.template().body().length))));
  }

  @Test
  void headersContainsWhitespaces() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(HeadersContainsWhitespaces.class, "post");

    assertThat(md.template())
        .hasHeaders(
            entry("Content-Type", Collections.singletonList("application/xml")),
            entry("Content-Length", asList(String.valueOf(md.template().body().length))));
  }

  @Test
  void withPathAndURIParam() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(
            WithURIParam.class, "uriParam", String.class, URI.class, String.class);

    assertThat(md.indexToName())
        .containsExactly(
            entry(0, asList("1")),
            // Skips 1 as it is a url index!
            entry(2, asList("2")));

    assertThat(md.urlIndex()).isEqualTo(1);
  }

  @Test
  void pathAndQueryParams() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(
            WithPathAndQueryParams.class,
            "recordsByNameAndType",
            int.class,
            String.class,
            String.class);

    assertThat(md.template())
        .hasQueries(entry("name", asList("{name}")), entry("type", asList("{type}")));

    assertThat(md.indexToName())
        .containsExactly(
            entry(0, asList("domainId")), entry(1, asList("name")), entry(2, asList("type")));
  }

  @Test
  void autoDiscoverParamNames() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(
            AutoDiscoverParamNames.class,
            "recordsByNameAndType",
            int.class,
            String.class,
            String.class);

    assertThat(md.template())
        .hasQueries(entry("name", asList("{name}")), entry("type", asList("{type}")));

    assertThat(md.indexToName())
        .containsExactly(
            entry(0, asList("domainId")), entry(1, asList("name")), entry(2, asList("type")));
  }

  @Test
  void bodyWithTemplate() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(
            FormParams.class, "login", String.class, String.class, String.class);

    assertThat(md.template())
        .hasBodyTemplate(
            "%7B\"customer_name\": \"{customer_name}\", \"user_name\": \"{user_name}\","
                + " \"password\": \"{password}\"%7D");
  }

  @Test
  void formParamsParseIntoIndexToName() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(
            FormParams.class, "login", String.class, String.class, String.class);

    assertThat(md.formParams()).containsExactly("customer_name", "user_name", "password");

    assertThat(md.indexToName())
        .containsExactly(
            entry(0, asList("customer_name")),
            entry(1, asList("user_name")),
            entry(2, asList("password")));
  }

  @Test
  void formParamAndBodyParams() throws Exception {
    Throwable exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                parseAndValidateMetadata(
                    FormParams.class, "formParamAndBodyParams", String.class, String.class));
    assertThat(exception.getMessage())
        .contains("Body parameters cannot be used with form parameters.");
  }

  @Test
  void bodyParamsAndformParam() throws Exception {
    Throwable exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                parseAndValidateMetadata(
                    FormParams.class, "bodyParamsAndformParam", String.class, String.class));
    assertThat(exception.getMessage())
        .contains("Body parameters cannot be used with form parameters.");
  }

  @Test
  void formParamParseIntoFormParams() throws Exception {

    MethodMetadata md =
        parseAndValidateMetadata(
            FormParams.class, "loginNoBodyTemplate", String.class, String.class, String.class);

    assertThat(md.formParams()).containsExactly("customer_name", "user_name", "password");

    assertThat(md.indexToName())
        .containsExactly(
            entry(0, asList("customer_name")),
            entry(1, asList("user_name")),
            entry(2, asList("password")));
  }

  /** Body type is only for the body param. */
  @Test
  void formParamsDoesNotSetBodyType() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(
            FormParams.class, "login", String.class, String.class, String.class);

    assertThat(md.bodyType()).isNull();
  }

  @Test
  void headerParamsParseIntoIndexToName() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(HeaderParams.class, "logout", String.class);

    assertThat(md.template()).hasHeaders(entry("Auth-Token", asList("{authToken}", "Foo")));

    assertThat(md.indexToName()).containsExactly(entry(0, asList("authToken")));
    assertThat(md.formParams()).isEmpty();
  }

  @Test
  void headerParamsParseIntoIndexToNameNotAtStart() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(HeaderParamsNotAtStart.class, "logout", String.class);

    assertThat(md.template())
        .hasHeaders(entry("Authorization", asList("Bearer {authToken}", "Foo")));

    assertThat(md.indexToName()).containsExactly(entry(0, asList("authToken")));
    assertThat(md.formParams()).isEmpty();
  }

  @Test
  void customExpander() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(CustomExpander.class, "clock", Clock.class);

    assertThat(md.indexToExpanderClass()).containsExactly(entry(0, ClockToMillis.class));
  }

  @Test
  void queryMap() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(QueryMapTestInterface.class, "queryMap", Map.class);

    assertThat(md.queryMapIndex()).isEqualTo(0);
  }

  @Test
  void queryMapMapSubclass() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(
            QueryMapTestInterface.class, "queryMapMapSubclass", SortedMap.class);

    assertThat(md.queryMapIndex()).isEqualTo(0);
  }

  @Test
  void onlyOneQueryMapAnnotationPermitted() throws Exception {
    try {
      parseAndValidateMetadata(
          QueryMapTestInterface.class, "multipleQueryMap", Map.class, Map.class);
      Fail.failBecauseExceptionWasNotThrown(IllegalStateException.class);
    } catch (final IllegalStateException ex) {
      assertThat(ex).hasMessage("QueryMap annotation was present on multiple parameters.");
    }
  }

  @Test
  void queryMapKeysMustBeStrings() throws Exception {
    try {
      parseAndValidateMetadata(QueryMapTestInterface.class, "nonStringKeyQueryMap", Map.class);
      Fail.failBecauseExceptionWasNotThrown(IllegalStateException.class);
    } catch (final IllegalStateException ex) {
      assertThat(ex).hasMessage("QueryMap key must be a String: Integer");
    }
  }

  @Test
  void queryMapPojoObject() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(QueryMapTestInterface.class, "pojoObject", Object.class);

    assertThat(md.queryMapIndex()).isEqualTo(0);
  }

  @Test
  void slashAreEncodedWhenNeeded() throws Exception {
    MethodMetadata md =
        parseAndValidateMetadata(SlashNeedToBeEncoded.class, "getQueues", String.class);

    assertThat(md.template().decodeSlash()).isFalse();

    md = parseAndValidateMetadata(SlashNeedToBeEncoded.class, "getZone", String.class);

    assertThat(md.template().decodeSlash()).isTrue();
  }

  @Test
  void onlyOneHeaderMapAnnotationPermitted() throws Exception {
    try {
      parseAndValidateMetadata(HeaderMapInterface.class, "multipleHeaderMap", Map.class, Map.class);
      Fail.failBecauseExceptionWasNotThrown(IllegalStateException.class);
    } catch (final IllegalStateException ex) {
      assertThat(ex).hasMessage("HeaderMap annotation was present on multiple parameters.");
    }
  }

  @Test
  void headerMapSubclass() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(
            HeaderMapInterface.class, "headerMapSubClass", SubClassHeaders.class);
    assertThat(md.headerMapIndex()).isEqualTo(0);
  }

  @Test
  void headerMapUserObject() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(
            HeaderMapInterface.class, "headerMapUserObject", HeaderMapUserObject.class);
    assertThat(md.headerMapIndex()).isEqualTo(0);
  }

  interface Methods {

    @RequestLine("POST /")
    void post();

    @RequestLine("PUT /")
    void put();

    @RequestLine("GET /")
    void get();

    @RequestLine("DELETE /")
    void delete();
  }

  interface BodyParams {

    @RequestLine("POST")
    Response post(List<String> body);

    @RequestLine("PUT /offers/{id}")
    void post(@Param("id") int id, List<String> body);

    @RequestLine("POST")
    Response tooMany(List<String> body, List<String> body2);
  }

  interface CustomMethod {

    @RequestLine("PATCH")
    Response patch();
  }

  interface WithQueryParamsInPath {

    @RequestLine("GET /")
    Response none();

    @RequestLine("GET /?Action=GetUser")
    Response one();

    @RequestLine("GET /?Action=GetUser&Version=2010-05-08")
    Response two();

    @RequestLine("GET /?Action=GetUser&Version=2010-05-08&limit=1")
    Response three();

    @RequestLine("GET /?flag&Action=GetUser&Version=2010-05-08")
    Response twoAndOneEmpty();

    @RequestLine("GET /?flag")
    Response oneEmpty();

    @RequestLine("GET /?flag&NoErrors")
    Response twoEmpty();
  }

  interface BodyWithoutParameters {

    @RequestLine("POST /")
    @Headers("Content-Type: application/xml")
    @Body("<v01:getAccountsListOfUser/>")
    Response post();
  }

  @Headers("Content-Type: application/xml")
  interface HeadersOnType {

    @RequestLine("POST /")
    @Body("<v01:getAccountsListOfUser/>")
    Response post();
  }

  @Headers("Content-Type:    application/xml   ")
  interface HeadersContainsWhitespaces {

    @RequestLine("POST /")
    @Body("<v01:getAccountsListOfUser/>")
    Response post();
  }

  interface WithURIParam {

    @RequestLine("GET /{1}/{2}")
    Response uriParam(@Param("1") String one, URI endpoint, @Param("2") String two);
  }

  interface WithPathAndQueryParams {

    @RequestLine("GET /domains/{domainId}/records?name={name}&type={type}")
    Response recordsByNameAndType(
        @Param("domainId") int id,
        @Param("name") String nameFilter,
        @Param("type") String typeFilter);
  }

  interface AutoDiscoverParamNames {

    @RequestLine("GET /domains/{domainId}/records?name={name}&type={type}")
    Response recordsByNameAndType(
        @Param("domainId") int domainId, @Param("name") String name, @Param("type") String type);
  }

  interface FormParams {

    @RequestLine("POST /")
    @Body(
        "%7B\"customer_name\": \"{customer_name}\", \"user_name\": \"{user_name}\", \"password\":"
            + " \"{password}\"%7D")
    void login(
        @Param("customer_name") String customer,
        @Param("user_name") String user,
        @Param("password") String password);

    @RequestLine("POST /")
    void loginNoBodyTemplate(
        @Param("customer_name") String customer,
        @Param("user_name") String user,
        @Param("password") String password);

    @RequestLine("POST /")
    void formParamAndBodyParams(@Param("customer_name") String customer, String body);

    @RequestLine("POST /")
    void bodyParamsAndformParam(String body, @Param("customer_name") String customer);
  }

  interface HeaderMapInterface {

    @RequestLine("POST /")
    void multipleHeaderMap(
        @HeaderMap Map<String, String> headers, @HeaderMap Map<String, String> queries);

    @RequestLine("POST /")
    void headerMapSubClass(@HeaderMap SubClassHeaders httpHeaders);

    @RequestLine("POST /")
    void headerMapUserObject(@HeaderMap HeaderMapUserObject httpHeaders);
  }

  class HeaderMapUserObject {
    @Param("name1")
    private String name;

    @Param("grade1")
    private String grade;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getGrade() {
      return grade;
    }

    public void setGrade(String grade) {
      this.grade = grade;
    }
  }

  interface HeaderParams {

    @RequestLine("POST /")
    @Headers({"Auth-Token: {authToken}", "Auth-Token: Foo"})
    void logout(@Param("authToken") String token);
  }

  interface HeaderParamsNotAtStart {

    @RequestLine("POST /")
    @Headers({"Authorization: Bearer {authToken}", "Authorization: Foo"})
    void logout(@Param("authToken") String token);
  }

  interface CustomExpander {

    @RequestLine("POST /?clock={clock}")
    void clock(@Param(value = "clock", expander = ClockToMillis.class) Clock clock);
  }

  class ClockToMillis implements Param.Expander {

    @Override
    public String expand(Object value) {
      return String.valueOf(((Clock) value).millis());
    }
  }

  interface QueryMapTestInterface {

    @RequestLine("POST /")
    void queryMap(@QueryMap Map<String, String> queryMap);

    @RequestLine("POST /")
    void queryMapMapSubclass(@QueryMap SortedMap<String, String> queryMap);

    @RequestLine("POST /")
    void pojoObject(@QueryMap Object object);

    // invalid
    @RequestLine("POST /")
    void multipleQueryMap(
        @QueryMap Map<String, String> mapOne, @QueryMap Map<String, String> mapTwo);

    // invalid
    @RequestLine("POST /")
    void nonStringKeyQueryMap(@QueryMap Map<Integer, String> queryMap);
  }

  interface SlashNeedToBeEncoded {
    @RequestLine(value = "GET /api/queues/{vhost}", decodeSlash = false)
    String getQueues(@Param("vhost") String vhost);

    @RequestLine(value = "GET /api/{zoneId}")
    String getZone(@Param("ZoneId") String vhost);
  }

  @Headers("Foo: Bar")
  interface ParameterizedBaseApi<K, M> {

    @RequestLine("GET /api/{key}")
    Entity<K, M> get(@Param("key") K key);

    @RequestLine("POST /api")
    Entities<K, M> getAll(Keys<K> keys);
  }

  static class Keys<K> {

    List<K> keys;
  }

  static class Entity<K, M> {

    K key;
    M model;
  }

  static class Entities<K, M> {

    private List<Entity<K, M>> entities;
  }

  interface SubClassHeaders extends Map<String, String> {}

  @Headers("Version: 1")
  interface ParameterizedApi extends ParameterizedBaseApi<String, Long> {}

  @Test
  void parameterizedBaseApi() throws Exception {
    final List<MethodMetadata> md = contract.parseAndValidateMetadata(ParameterizedApi.class);

    final Map<String, MethodMetadata> byConfigKey = new LinkedHashMap<>();
    for (final MethodMetadata m : md) {
      byConfigKey.put(m.configKey(), m);
    }

    assertThat(byConfigKey)
        .containsOnlyKeys("ParameterizedApi#get(String)", "ParameterizedApi#getAll(Keys)");

    assertThat(byConfigKey.get("ParameterizedApi#get(String)").returnType())
        .isEqualTo(new TypeToken<Entity<String, Long>>() {}.getType());
    assertThat(byConfigKey.get("ParameterizedApi#get(String)").template())
        .hasHeaders(entry("Version", asList("1")), entry("Foo", asList("Bar")));

    assertThat(byConfigKey.get("ParameterizedApi#getAll(Keys)").returnType())
        .isEqualTo(new TypeToken<Entities<String, Long>>() {}.getType());
    assertThat(byConfigKey.get("ParameterizedApi#getAll(Keys)").bodyType())
        .isEqualTo(new TypeToken<Keys<String>>() {}.getType());
    assertThat(byConfigKey.get("ParameterizedApi#getAll(Keys)").template())
        .hasHeaders(entry("Version", asList("1")), entry("Foo", asList("Bar")));
  }

  @Headers("Authorization: {authHdr}")
  interface ParameterizedHeaderExpandApi {
    @RequestLine("GET /api/{zoneId}")
    @Headers("Accept: application/json")
    String getZone(@Param("zoneId") String vhost, @Param("authHdr") String authHdr);
  }

  @Test
  void parameterizedHeaderExpandApi() throws Exception {
    final List<MethodMetadata> md =
        contract.parseAndValidateMetadata(ParameterizedHeaderExpandApi.class);

    assertThat(md).hasSize(1);

    assertThat(md.getFirst().configKey())
        .isEqualTo("ParameterizedHeaderExpandApi#getZone(String,String)");
    assertThat(md.getFirst().returnType()).isEqualTo(String.class);
    assertThat(md.getFirst().template())
        .hasHeaders(
            entry("Authorization", asList("{authHdr}")),
            entry("Accept", asList("application/json")));
    // Ensure that the authHdr expansion was properly detected and did not create a
    // formParam
    assertThat(md.getFirst().formParams()).isEmpty();
  }

  @Test
  void parameterizedHeaderNotStartingWithCurlyBraceExpandApi() throws Exception {
    final List<MethodMetadata> md =
        contract.parseAndValidateMetadata(
            ParameterizedHeaderNotStartingWithCurlyBraceExpandApi.class);

    assertThat(md).hasSize(1);

    assertThat(md.getFirst().configKey())
        .isEqualTo("ParameterizedHeaderNotStartingWithCurlyBraceExpandApi#getZone(String,String)");
    assertThat(md.getFirst().returnType()).isEqualTo(String.class);
    assertThat(md.getFirst().template())
        .hasHeaders(
            entry("Authorization", asList("Bearer {authHdr}")),
            entry("Accept", asList("application/json")));
    // Ensure that the authHdr expansion was properly detected and did not create a
    // formParam
    assertThat(md.getFirst().formParams()).isEmpty();
  }

  @Headers("Authorization: Bearer {authHdr}")
  interface ParameterizedHeaderNotStartingWithCurlyBraceExpandApi {
    @RequestLine("GET /api/{zoneId}")
    @Headers("Accept: application/json")
    String getZone(@Param("zoneId") String vhost, @Param("authHdr") String authHdr);
  }

  @Headers("Authorization: {authHdr}")
  interface ParameterizedHeaderBase {}

  interface ParameterizedHeaderExpandInheritedApi extends ParameterizedHeaderBase {
    @RequestLine("GET /api/{zoneId}")
    @Headers("Accept: application/json")
    String getZoneAccept(@Param("zoneId") String vhost, @Param("authHdr") String authHdr);

    @RequestLine("GET /api/{zoneId}")
    String getZone(@Param("zoneId") String vhost, @Param("authHdr") String authHdr);
  }

  @Test
  void parameterizedHeaderExpandApiBaseClass() throws Exception {
    final List<MethodMetadata> mds =
        contract.parseAndValidateMetadata(ParameterizedHeaderExpandInheritedApi.class);

    final Map<String, MethodMetadata> byConfigKey = new LinkedHashMap<>();
    for (final MethodMetadata m : mds) {
      byConfigKey.put(m.configKey(), m);
    }

    assertThat(byConfigKey)
        .containsOnlyKeys(
            "ParameterizedHeaderExpandInheritedApi#getZoneAccept(String,String)",
            "ParameterizedHeaderExpandInheritedApi#getZone(String,String)");

    MethodMetadata md =
        byConfigKey.get("ParameterizedHeaderExpandInheritedApi#getZoneAccept(String,String)");
    assertThat(md.returnType()).isEqualTo(String.class);
    assertThat(md.template())
        .hasHeaders(
            entry("Authorization", asList("{authHdr}")),
            entry("Accept", asList("application/json")));
    // Ensure that the authHdr expansion was properly detected and did not create a
    // formParam
    assertThat(md.formParams()).isEmpty();

    md = byConfigKey.get("ParameterizedHeaderExpandInheritedApi#getZone(String,String)");
    assertThat(md.returnType()).isEqualTo(String.class);
    assertThat(md.template()).hasHeaders(entry("Authorization", asList("{authHdr}")));
    assertThat(md.formParams()).isEmpty();
  }

  private MethodMetadata parseAndValidateMetadata(
      Class<?> targetType, String method, Class<?>... parameterTypes) throws NoSuchMethodException {
    return contract.parseAndValidateMetadata(
        targetType, targetType.getMethod(method, parameterTypes));
  }

  interface MissingMethod {
    @RequestLine("/path?queryParam={queryParam}")
    Response updateSharing(@Param("queryParam") long queryParam, String bodyParam);
  }

  /** Let's help folks not lose time when they mistake request line for a URI! */
  @Test
  void missingMethod() throws Exception {
    Throwable exception =
        assertThrows(
            IllegalStateException.class,
            () -> contract.parseAndValidateMetadata(MissingMethod.class));
    assertThat(exception.getMessage())
        .contains(
            "RequestLine annotation didn't start with an HTTP verb on method"
                + " MissingMethod#updateSharing");
  }

  interface StaticMethodOnInterface {
    @RequestLine("GET /api/{key}")
    String get(@Param("key") String key);

    static String staticMethod() {
      return "value";
    }
  }

  @Test
  void staticMethodsOnInterfaceIgnored() throws Exception {
    final List<MethodMetadata> mds =
        contract.parseAndValidateMetadata(StaticMethodOnInterface.class);
    assertThat(mds).hasSize(1);
    final MethodMetadata md = mds.getFirst();
    assertThat(md.configKey()).isEqualTo("StaticMethodOnInterface#get(String)");
  }

  interface DefaultMethodOnInterface {
    @RequestLine("GET /api/{key}")
    String get(@Param("key") String key);

    default String defaultGet(String key) {
      return get(key);
    }
  }

  @Test
  void defaultMethodsOnInterfaceIgnored() throws Exception {
    final List<MethodMetadata> mds =
        contract.parseAndValidateMetadata(DefaultMethodOnInterface.class);
    assertThat(mds).hasSize(1);
    final MethodMetadata md = mds.getFirst();
    assertThat(md.configKey()).isEqualTo("DefaultMethodOnInterface#get(String)");
  }

  interface SubstringQuery {
    @RequestLine("GET /_search?q=body:{body}")
    String paramIsASubstringOfAQuery(@Param("body") String body);
  }

  @Test
  void paramIsASubstringOfAQuery() throws Exception {
    final List<MethodMetadata> mds = contract.parseAndValidateMetadata(SubstringQuery.class);

    assertThat(mds.getFirst().template().queries())
        .containsExactly(entry("q", asList("body:{body}")));
    assertThat(mds.getFirst().formParams()).isEmpty(); // Prevent issue 424
  }

  @Test
  void errorMessageOnMixedContracts() {
    Throwable exception =
        assertThrows(
            IllegalStateException.class,
            () -> contract.parseAndValidateMetadata(MixedAnnotations.class));
    assertThat(exception.getMessage()).contains("are not used by contract Default");
  }

  interface MixedAnnotations {
    @Headers("Content-Type: application/json")
    @RequestLine("GET api/v2/clients/{uid}")
    Response findAllClientsByUid2(
        @UselessAnnotation("only used to cause problems") String uid,
        Integer limit,
        @SuppressWarnings({"a"}) Integer offset);
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.PARAMETER)
  public static @interface UselessAnnotation {
    String value() default "";
  }

  class TestClock extends Clock {

    private long millis;

    public TestClock(long millis) {
      this.millis = millis;
    }

    @Override
    public ZoneId getZone() {
      throw new UnsupportedOperationException("This operation is not supported.");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return Instant.ofEpochMilli(millis);
    }
  }

  interface NoValueParamsInterface {
    @RequestLine("GET /getSomething?id={id}")
    String getSomething(@Param String id);
  }

  @Test
  void errorMessageOnMissingParamNames() {
    Assumptions.assumeTrue(
        !isCompiledWithParameters(NoValueParamsInterface.class), "Skip if -parameters is enabled");

    Throwable exception =
        assertThrows(
            IllegalStateException.class,
            () -> contract.parseAndValidateMetadata(NoValueParamsInterface.class));

    assertThat(exception.getMessage())
        .contains("Param annotation was empty on param 0")
        .contains("Hint");
  }

  /**
   * Checks whether the given class was compiled with the `-parameters` compiler flag. If parameter
   * names are preserved (i.e., not default like arg0, arg1...), we assume it was.
   *
   * @param clazz the class to inspect
   * @return true if `-parameters` was likely used, false otherwise
   */
  private static boolean isCompiledWithParameters(Class<?> clazz) {
    for (Method method : clazz.getDeclaredMethods()) {
      for (Parameter parameter : method.getParameters()) {
        String paramName = parameter.getName();
        if (!paramName.matches("arg\\d+")) {
          return true;
        }
      }
    }
    return false;
  }
}
