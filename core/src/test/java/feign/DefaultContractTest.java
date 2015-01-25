/*
 * Copyright 2013 Netflix, Inc.
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

import static feign.Util.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.gson.reflect.TypeToken;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import javax.inject.Named;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * Tests interfaces defined per {@link Contract.Default} are interpreted into expected {@link feign
 * .RequestTemplate template} instances.
 */
public class DefaultContractTest {
  @Rule public final ExpectedException thrown = ExpectedException.none();

  Contract.Default contract = new Contract.Default();

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

  @Test
  public void httpMethods() throws Exception {
    assertEquals(
        "POST",
        contract
            .parseAndValidatateMetadata(Methods.class.getDeclaredMethod("post"))
            .template()
            .method());
    assertEquals(
        "PUT",
        contract
            .parseAndValidatateMetadata(Methods.class.getDeclaredMethod("put"))
            .template()
            .method());
    assertEquals(
        "GET",
        contract
            .parseAndValidatateMetadata(Methods.class.getDeclaredMethod("get"))
            .template()
            .method());
    assertEquals(
        "DELETE",
        contract
            .parseAndValidatateMetadata(Methods.class.getDeclaredMethod("delete"))
            .template()
            .method());
  }

  interface BodyParams {
    @RequestLine("POST")
    Response post(List<String> body);

    @RequestLine("POST")
    Response tooMany(List<String> body, List<String> body2);
  }

  @Test
  public void bodyParamIsGeneric() throws Exception {
    MethodMetadata md =
        contract.parseAndValidatateMetadata(BodyParams.class.getDeclaredMethod("post", List.class));
    assertNull(md.template().body());
    assertNull(md.template().bodyTemplate());
    assertNull(md.urlIndex());
    assertEquals(md.bodyIndex(), Integer.valueOf(0));
    assertEquals(md.bodyType(), new TypeToken<List<String>>() {}.getType());
  }

  @Test
  public void tooManyBodies() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Method has too many Body");
    contract.parseAndValidatateMetadata(
        BodyParams.class.getDeclaredMethod("tooMany", List.class, List.class));
  }

  interface CustomMethodAndURIParam {
    @RequestLine("PATCH")
    Response patch(URI nextLink);
  }

  @Test
  public void requestLineOnlyRequiresMethod() throws Exception {
    MethodMetadata md =
        contract.parseAndValidatateMetadata(
            CustomMethodAndURIParam.class.getDeclaredMethod("patch", URI.class));
    assertEquals("PATCH", md.template().method());
    assertEquals("", md.template().url());
    assertTrue(md.template().queries().isEmpty());
    assertTrue(md.template().headers().isEmpty());
    assertNull(md.template().body());
    assertNull(md.template().bodyTemplate());
    assertEquals(Integer.valueOf(0), md.urlIndex());
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
    Response empty();
  }

  @Test
  public void queryParamsInPathExtract() throws Exception {
    {
      MethodMetadata md =
          contract.parseAndValidatateMetadata(
              WithQueryParamsInPath.class.getDeclaredMethod("none"));
      assertEquals("/", md.template().url());
      assertTrue(md.template().queries().isEmpty());
      assertEquals("GET / HTTP/1.1\n", md.template().toString());
    }
    {
      MethodMetadata md =
          contract.parseAndValidatateMetadata(WithQueryParamsInPath.class.getDeclaredMethod("one"));
      assertEquals("/", md.template().url());
      assertEquals(Arrays.asList("GetUser"), md.template().queries().get("Action"));
      assertEquals("GET /?Action=GetUser HTTP/1.1\n", md.template().toString());
    }
    {
      MethodMetadata md =
          contract.parseAndValidatateMetadata(WithQueryParamsInPath.class.getDeclaredMethod("two"));
      assertEquals("/", md.template().url());
      assertEquals(Arrays.asList("GetUser"), md.template().queries().get("Action"));
      assertEquals(Arrays.asList("2010-05-08"), md.template().queries().get("Version"));
      assertEquals("GET /?Action=GetUser&Version=2010-05-08 HTTP/1.1\n", md.template().toString());
    }
    {
      MethodMetadata md =
          contract.parseAndValidatateMetadata(
              WithQueryParamsInPath.class.getDeclaredMethod("three"));
      assertEquals("/", md.template().url());
      assertEquals(Arrays.asList("GetUser"), md.template().queries().get("Action"));
      assertEquals(Arrays.asList("2010-05-08"), md.template().queries().get("Version"));
      assertEquals(Arrays.asList("1"), md.template().queries().get("limit"));
      assertEquals(
          "GET /?Action=GetUser&Version=2010-05-08&limit=1 HTTP/1.1\n", md.template().toString());
    }
    {
      MethodMetadata md =
          contract.parseAndValidatateMetadata(
              WithQueryParamsInPath.class.getDeclaredMethod("empty"));
      assertEquals("/", md.template().url());
      assertTrue(md.template().queries().containsKey("flag"));
      assertEquals(Arrays.asList("GetUser"), md.template().queries().get("Action"));
      assertEquals(Arrays.asList("2010-05-08"), md.template().queries().get("Version"));
      assertEquals(
          "GET /?flag&Action=GetUser&Version=2010-05-08 HTTP/1.1\n", md.template().toString());
    }
  }

  interface BodyWithoutParameters {
    @RequestLine("POST /")
    @Headers("Content-Type: application/xml")
    @Body("<v01:getAccountsListOfUser/>")
    Response post();
  }

  @Test
  public void bodyWithoutParameters() throws Exception {
    MethodMetadata md =
        contract.parseAndValidatateMetadata(BodyWithoutParameters.class.getDeclaredMethod("post"));
    assertEquals("<v01:getAccountsListOfUser/>", new String(md.template().body(), UTF_8));
    assertFalse(md.template().bodyTemplate() != null);
    assertTrue(md.formParams().isEmpty());
    assertTrue(md.indexToName().isEmpty());
  }

  @Test
  public void producesAddsContentTypeHeader() throws Exception {
    MethodMetadata md =
        contract.parseAndValidatateMetadata(BodyWithoutParameters.class.getDeclaredMethod("post"));
    assertEquals(Arrays.asList("application/xml"), md.template().headers().get("Content-Type"));
  }

  interface WithURIParam {
    @RequestLine("GET /{1}/{2}")
    Response uriParam(@Named("1") String one, URI endpoint, @Named("2") String two);
  }

  @Test
  public void methodCanHaveUriParam() throws Exception {
    MethodMetadata md =
        contract.parseAndValidatateMetadata(
            WithURIParam.class.getDeclaredMethod(
                "uriParam", String.class, URI.class, String.class));
    assertEquals(Integer.valueOf(1), md.urlIndex());
  }

  @Test
  public void pathParamsParseIntoIndexToName() throws Exception {
    MethodMetadata md =
        contract.parseAndValidatateMetadata(
            WithURIParam.class.getDeclaredMethod(
                "uriParam", String.class, URI.class, String.class));
    assertEquals("/{1}/{2}", md.template().url());
    assertEquals(Arrays.asList("1"), md.indexToName().get(0));
    assertEquals(Arrays.asList("2"), md.indexToName().get(2));
  }

  interface WithPathAndQueryParams {
    @RequestLine("GET /domains/{domainId}/records?name={name}&type={type}")
    Response recordsByNameAndType(
        @Named("domainId") int id,
        @Named("name") String nameFilter,
        @Named("type") String typeFilter);
  }

  @Test
  public void mixedRequestLineParams() throws Exception {
    MethodMetadata md =
        contract.parseAndValidatateMetadata(
            WithPathAndQueryParams.class.getDeclaredMethod(
                "recordsByNameAndType", int.class, String.class, String.class));
    assertNull(md.template().body());
    assertNull(md.template().bodyTemplate());
    assertTrue(md.template().headers().isEmpty());
    assertEquals("/domains/{domainId}/records", md.template().url());
    assertEquals(Arrays.asList("{name}"), md.template().queries().get("name"));
    assertEquals(Arrays.asList("{type}"), md.template().queries().get("type"));
    assertEquals(Arrays.asList("domainId"), md.indexToName().get(0));
    assertEquals(Arrays.asList("name"), md.indexToName().get(1));
    assertEquals(Arrays.asList("type"), md.indexToName().get(2));
    assertEquals(
        "GET /domains/{domainId}/records?name={name}&type={type} HTTP/1.1\n",
        md.template().toString());
  }

  interface FormParams {
    @RequestLine("POST /")
    @Body(
        "%7B\"customer_name\": \"{customer_name}\", \"user_name\": \"{user_name}\", \"password\":"
            + " \"{password}\"%7D")
    void login(
        @Named("customer_name") String customer,
        @Named("user_name") String user,
        @Named("password") String password);
  }

  @Test
  public void formParamsParseIntoIndexToName() throws Exception {
    MethodMetadata md =
        contract.parseAndValidatateMetadata(
            FormParams.class.getDeclaredMethod("login", String.class, String.class, String.class));

    assertFalse(md.template().body() != null);
    assertEquals(
        "%7B\"customer_name\": \"{customer_name}\", \"user_name\": \"{user_name}\", \"password\":"
            + " \"{password}\"%7D",
        md.template().bodyTemplate());
    assertEquals(ImmutableList.of("customer_name", "user_name", "password"), md.formParams());
    assertEquals(Arrays.asList("customer_name"), md.indexToName().get(0));
    assertEquals(Arrays.asList("user_name"), md.indexToName().get(1));
    assertEquals(Arrays.asList("password"), md.indexToName().get(2));
  }

  interface HeaderParams {
    @RequestLine("POST /")
    @Headers("Auth-Token: {Auth-Token}")
    void logout(@Named("Auth-Token") String token);
  }

  @Test
  public void headerParamsParseIntoIndexToName() throws Exception {
    MethodMetadata md =
        contract.parseAndValidatateMetadata(
            HeaderParams.class.getDeclaredMethod("logout", String.class));

    assertEquals(Arrays.asList("{Auth-Token}"), md.template().headers().get("Auth-Token"));
    assertEquals(Arrays.asList("Auth-Token"), md.indexToName().get(0));
  }
}
