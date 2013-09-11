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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.reflect.TypeToken;
import org.testng.annotations.Test;

import javax.inject.Named;
import java.net.URI;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests interfaces defined per {@link Contract.Default} are interpreted into expected {@link feign
 * .RequestTemplate template}
 * instances.
 */
@Test
public class DefaultContractTest {
  Contract.Default contract = new Contract.Default();

  interface Methods {
    @RequestLine("POST /") void post();

    @RequestLine("PUT /") void put();

    @RequestLine("GET /") void get();

    @RequestLine("DELETE /") void delete();
  }

  @Test public void httpMethods() throws Exception {
    assertEquals(contract.parseAndValidatateMetadata(Methods.class.getDeclaredMethod("post")).template().method(),
        "POST");
    assertEquals(contract.parseAndValidatateMetadata(Methods.class.getDeclaredMethod("put")).template().method(),
        "PUT");
    assertEquals(contract.parseAndValidatateMetadata(Methods.class.getDeclaredMethod("get")).template().method(),
        "GET");
    assertEquals(contract.parseAndValidatateMetadata(Methods.class.getDeclaredMethod("delete")).template().method(),
        "DELETE");
  }

  interface BodyParams {
    @RequestLine("POST") Response post(List<String> body);

    @RequestLine("POST") Response tooMany(List<String> body, List<String> body2);
  }

  @Test public void bodyParamIsGeneric() throws Exception {
    MethodMetadata md = contract.parseAndValidatateMetadata(BodyParams.class.getDeclaredMethod("post",
        List.class));
    assertNull(md.template().body());
    assertNull(md.template().bodyTemplate());
    assertNull(md.urlIndex());
    assertEquals(md.bodyIndex(), Integer.valueOf(0));
    assertEquals(md.bodyType(), new TypeToken<List<String>>() {
    }.getType());
  }

  @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "Method has too many Body.*")
  public void tooManyBodies() throws Exception {
    contract.parseAndValidatateMetadata(BodyParams.class.getDeclaredMethod("tooMany", List.class, List.class));
  }

  interface CustomMethodAndURIParam {
    @RequestLine("PATCH") Response patch(URI nextLink);
  }

  @Test public void requestLineOnlyRequiresMethod() throws Exception {
    MethodMetadata md = contract.parseAndValidatateMetadata(CustomMethodAndURIParam.class.getDeclaredMethod("patch",
        URI.class));
    assertEquals(md.template().method(), "PATCH");
    assertEquals(md.template().url(), "");
    assertTrue(md.template().queries().isEmpty());
    assertTrue(md.template().headers().isEmpty());
    assertNull(md.template().body());
    assertNull(md.template().bodyTemplate());
    assertEquals(md.urlIndex(), Integer.valueOf(0));
  }

  interface WithQueryParamsInPath {
    @RequestLine("GET /") Response none();

    @RequestLine("GET /?Action=GetUser") Response one();

    @RequestLine("GET /?Action=GetUser&Version=2010-05-08") Response two();

    @RequestLine("GET /?Action=GetUser&Version=2010-05-08&limit=1") Response three();

    @RequestLine("GET /?flag&Action=GetUser&Version=2010-05-08") Response empty();
  }

  @Test public void queryParamsInPathExtract() throws Exception {
    {
      MethodMetadata md = contract.parseAndValidatateMetadata(WithQueryParamsInPath.class.getDeclaredMethod("none"));
      assertEquals(md.template().url(), "/");
      assertTrue(md.template().queries().isEmpty());
      assertEquals(md.template().toString(), "GET / HTTP/1.1\n");
    }
    {
      MethodMetadata md = contract.parseAndValidatateMetadata(WithQueryParamsInPath.class.getDeclaredMethod("one"));
      assertEquals(md.template().url(), "/");
      assertEquals(md.template().queries().get("Action"), ImmutableSet.of("GetUser"));
      assertEquals(md.template().toString(), "GET /?Action=GetUser HTTP/1.1\n");
    }
    {
      MethodMetadata md = contract.parseAndValidatateMetadata(WithQueryParamsInPath.class.getDeclaredMethod("two"));
      assertEquals(md.template().url(), "/");
      assertEquals(md.template().queries().get("Action"), ImmutableSet.of("GetUser"));
      assertEquals(md.template().queries().get("Version"), ImmutableSet.of("2010-05-08"));
      assertEquals(md.template().toString(), "GET /?Action=GetUser&Version=2010-05-08 HTTP/1.1\n");
    }
    {
      MethodMetadata md = contract.parseAndValidatateMetadata(WithQueryParamsInPath.class.getDeclaredMethod("three"));
      assertEquals(md.template().url(), "/");
      assertEquals(md.template().queries().get("Action"), ImmutableSet.of("GetUser"));
      assertEquals(md.template().queries().get("Version"), ImmutableSet.of("2010-05-08"));
      assertEquals(md.template().queries().get("limit"), ImmutableSet.of("1"));
      assertEquals(md.template().toString(), "GET /?Action=GetUser&Version=2010-05-08&limit=1 HTTP/1.1\n");
    }
    {
      MethodMetadata md = contract.parseAndValidatateMetadata(WithQueryParamsInPath.class.getDeclaredMethod("empty"));
      assertEquals(md.template().url(), "/");
      assertTrue(md.template().queries().containsKey("flag"));
      assertEquals(md.template().queries().get("Action"), ImmutableSet.of("GetUser"));
      assertEquals(md.template().queries().get("Version"), ImmutableSet.of("2010-05-08"));
      assertEquals(md.template().toString(), "GET /?flag&Action=GetUser&Version=2010-05-08 HTTP/1.1\n");
    }
  }

  interface BodyWithoutParameters {
    @RequestLine("POST /")
    @Headers("Content-Type: application/xml")
    @Body("<v01:getAccountsListOfUser/>") Response post();
  }

  @Test public void bodyWithoutParameters() throws Exception {
    MethodMetadata md = contract.parseAndValidatateMetadata(BodyWithoutParameters.class.getDeclaredMethod("post"));
    assertEquals(md.template().body(), "<v01:getAccountsListOfUser/>");
    assertFalse(md.template().bodyTemplate() != null);
    assertTrue(md.formParams().isEmpty());
    assertTrue(md.indexToName().isEmpty());
  }

  @Test public void producesAddsContentTypeHeader() throws Exception {
    MethodMetadata md = contract.parseAndValidatateMetadata(BodyWithoutParameters.class.getDeclaredMethod("post"));
    assertEquals(md.template().headers().get("Content-Type"), ImmutableSet.of("application/xml"));
  }

  interface WithURIParam {
    @RequestLine("GET /{1}/{2}") Response uriParam(@Named("1") String one, URI endpoint, @Named("2") String two);
  }

  @Test public void methodCanHaveUriParam() throws Exception {
    MethodMetadata md = contract.parseAndValidatateMetadata(WithURIParam.class.getDeclaredMethod("uriParam", String.class,
        URI.class, String.class));
    assertEquals(md.urlIndex(), Integer.valueOf(1));
  }

  @Test public void pathParamsParseIntoIndexToName() throws Exception {
    MethodMetadata md = contract.parseAndValidatateMetadata(WithURIParam.class.getDeclaredMethod("uriParam", String.class,
        URI.class, String.class));
    assertEquals(md.template().url(), "/{1}/{2}");
    assertEquals(md.indexToName().get(0), ImmutableSet.of("1"));
    assertEquals(md.indexToName().get(2), ImmutableSet.of("2"));
  }

  interface WithPathAndQueryParams {
    @RequestLine("GET /domains/{domainId}/records?name={name}&type={type}")
    Response recordsByNameAndType(@Named("domainId") int id, @Named("name") String nameFilter,
                                  @Named("type") String typeFilter);
  }

  @Test public void mixedRequestLineParams() throws Exception {
    MethodMetadata md = contract.parseAndValidatateMetadata(WithPathAndQueryParams.class.getDeclaredMethod
        ("recordsByNameAndType", int.class, String.class, String.class));
    assertNull(md.template().body());
    assertNull(md.template().bodyTemplate());
    assertTrue(md.template().headers().isEmpty());
    assertEquals(md.template().url(), "/domains/{domainId}/records");
    assertEquals(md.template().queries().get("name"), ImmutableSet.of("{name}"));
    assertEquals(md.template().queries().get("type"), ImmutableSet.of("{type}"));
    assertEquals(md.indexToName().get(0), ImmutableSet.of("domainId"));
    assertEquals(md.indexToName().get(1), ImmutableSet.of("name"));
    assertEquals(md.indexToName().get(2), ImmutableSet.of("type"));
    assertEquals(md.template().toString(), "GET /domains/{domainId}/records?name={name}&type={type} HTTP/1.1\n");
  }

  interface FormParams {
    @RequestLine("POST /")
    @Body("%7B\"customer_name\": \"{customer_name}\", \"user_name\": \"{user_name}\", \"password\": \"{password}\"%7D")
    void login(
        @Named("customer_name") String customer,
        @Named("user_name") String user, @Named("password") String password);
  }

  @Test public void formParamsParseIntoIndexToName() throws Exception {
    MethodMetadata md = contract.parseAndValidatateMetadata(FormParams.class.getDeclaredMethod("login", String.class,
        String.class, String.class));

    assertFalse(md.template().body() != null);
    assertEquals(md.template().bodyTemplate(),
        "%7B\"customer_name\": \"{customer_name}\", \"user_name\": \"{user_name}\", \"password\": \"{password}\"%7D");
    assertEquals(md.formParams(), ImmutableList.of("customer_name", "user_name", "password"));
    assertEquals(md.indexToName().get(0), ImmutableSet.of("customer_name"));
    assertEquals(md.indexToName().get(1), ImmutableSet.of("user_name"));
    assertEquals(md.indexToName().get(2), ImmutableSet.of("password"));
  }

  interface HeaderParams {
    @RequestLine("POST /")
    @Headers("Auth-Token: {Auth-Token}") void logout(@Named("Auth-Token") String token);
  }

  @Test public void headerParamsParseIntoIndexToName() throws Exception {
    MethodMetadata md = contract.parseAndValidatateMetadata(HeaderParams.class.getDeclaredMethod("logout", String.class));

    assertEquals(md.template().headers().get("Auth-Token"), ImmutableSet.of("{Auth-Token}"));
    assertEquals(md.indexToName().get(0), ImmutableSet.of("Auth-Token"));
  }
}
