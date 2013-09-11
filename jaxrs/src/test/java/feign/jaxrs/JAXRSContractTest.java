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
package feign.jaxrs;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.reflect.TypeToken;
import feign.MethodMetadata;
import feign.Response;
import org.testng.annotations.Test;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.util.List;

import static feign.jaxrs.JAXRSModule.ACCEPT;
import static feign.jaxrs.JAXRSModule.CONTENT_TYPE;
import static javax.ws.rs.HttpMethod.DELETE;
import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.HttpMethod.PUT;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests interfaces defined per {@link feign.jaxrs.JAXRSModule.JAXRSContract} are interpreted into expected {@link feign
 * .RequestTemplate template}
 * instances.
 */
@Test
public class JAXRSContractTest {
  JAXRSModule.JAXRSContract contract = new JAXRSModule.JAXRSContract();

  interface Methods {
    @POST void post();

    @PUT void put();

    @GET void get();

    @DELETE void delete();
  }

  @Test public void httpMethods() throws Exception {
    assertEquals(contract.parseAndValidatateMetadata(Methods.class.getDeclaredMethod("post")).template().method(),
        POST);
    assertEquals(contract.parseAndValidatateMetadata(Methods.class.getDeclaredMethod("put")).template().method(), PUT);
    assertEquals(contract.parseAndValidatateMetadata(Methods.class.getDeclaredMethod("get")).template().method(), GET);
    assertEquals(contract.parseAndValidatateMetadata(Methods.class.getDeclaredMethod("delete")).template().method(), DELETE);
  }

  interface CustomMethodAndURIParam {
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @HttpMethod("PATCH")
    public @interface PATCH {
    }

    @PATCH Response patch(URI nextLink);
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
    @GET @Path("/") Response none();

    @GET @Path("/?Action=GetUser") Response one();

    @GET @Path("/?Action=GetUser&Version=2010-05-08") Response two();

    @GET @Path("/?Action=GetUser&Version=2010-05-08&limit=1") Response three();

    @GET @Path("/?flag&Action=GetUser&Version=2010-05-08") Response empty();
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

  interface ProducesAndConsumes {
    @GET @Produces(APPLICATION_XML) Response produces();

    @GET @Produces({}) Response producesNada();

    @GET @Produces({""}) Response producesEmpty();

    @POST @Consumes(APPLICATION_JSON) Response consumes();

    @POST @Consumes({}) Response consumesNada();

    @POST @Consumes({""}) Response consumesEmpty();
  }

  @Test public void producesAddsAcceptHeader() throws Exception {
    MethodMetadata md = contract.parseAndValidatateMetadata(ProducesAndConsumes.class.getDeclaredMethod("produces"));
    assertEquals(md.template().headers().get(ACCEPT), ImmutableSet.of(APPLICATION_XML));
  }

  @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "Produces.value\\(\\) was empty on method producesNada")
  public void producesNada() throws Exception {
    contract.parseAndValidatateMetadata(ProducesAndConsumes.class.getDeclaredMethod("producesNada"));
  }

  @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "Produces.value\\(\\) was empty on method producesEmpty")
  public void producesEmpty() throws Exception {
    contract.parseAndValidatateMetadata(ProducesAndConsumes.class.getDeclaredMethod("producesEmpty"));
  }

  @Test public void consumesAddsContentTypeHeader() throws Exception {
    MethodMetadata md = contract.parseAndValidatateMetadata(ProducesAndConsumes.class.getDeclaredMethod("consumes"));
    assertEquals(md.template().headers().get(CONTENT_TYPE), ImmutableSet.of(APPLICATION_JSON));
  }

  @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "Consumes.value\\(\\) was empty on method consumesNada")
  public void consumesNada() throws Exception {
    contract.parseAndValidatateMetadata(ProducesAndConsumes.class.getDeclaredMethod("consumesNada"));
  }

  @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "Consumes.value\\(\\) was empty on method consumesEmpty")
  public void consumesEmpty() throws Exception {
    contract.parseAndValidatateMetadata(ProducesAndConsumes.class.getDeclaredMethod("consumesEmpty"));
  }

  interface BodyParams {
    @POST Response post(List<String> body);

    @POST Response tooMany(List<String> body, List<String> body2);
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

  @Path("") interface EmptyPathOnType {
    @GET Response base();
  }

  @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "Path.value\\(\\) was empty on type .*")
  public void emptyPathOnType() throws Exception {
    contract.parseAndValidatateMetadata(EmptyPathOnType.class.getDeclaredMethod("base"));
  }

  @Path("/base") interface PathOnType {
    @GET Response base();

    @GET @Path("/specific") Response get();

    @GET @Path("") Response emptyPath();

    @GET @Path("/{param}") Response emptyPathParam(@PathParam("") String empty);
  }

  @Test public void pathOnType() throws Exception {
    MethodMetadata md = contract.parseAndValidatateMetadata(PathOnType.class.getDeclaredMethod("base"));
    assertEquals(md.template().url(), "/base");
    md = contract.parseAndValidatateMetadata(PathOnType.class.getDeclaredMethod("get"));
    assertEquals(md.template().url(), "/base/specific");
  }

  @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "Path.value\\(\\) was empty on method emptyPath")
  public void emptyPathOnMethod() throws Exception {
    contract.parseAndValidatateMetadata(PathOnType.class.getDeclaredMethod("emptyPath"));
  }

  @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "PathParam.value\\(\\) was empty on parameter 0")
  public void emptyPathParam() throws Exception {
    contract.parseAndValidatateMetadata(PathOnType.class.getDeclaredMethod("emptyPathParam", String.class));
  }

  interface WithURIParam {
    @GET @Path("/{1}/{2}") Response uriParam(@PathParam("1") String one, URI endpoint, @PathParam("2") String two);
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
    @GET @Path("/domains/{domainId}/records")
    Response recordsByNameAndType(@PathParam("domainId") int id, @QueryParam("name") String nameFilter,
                                  @QueryParam("type") String typeFilter);

    @GET Response emptyQueryParam(@QueryParam("") String empty);
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

  @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "QueryParam.value\\(\\) was empty on parameter 0")
  public void emptyQueryParam() throws Exception {
    contract.parseAndValidatateMetadata(WithPathAndQueryParams.class.getDeclaredMethod("emptyQueryParam", String.class));
  }

  interface FormParams {
    @POST void login(
        @FormParam("customer_name") String customer,
        @FormParam("user_name") String user, @FormParam("password") String password);

    @GET Response emptyFormParam(@FormParam("") String empty);
  }

  @Test public void formParamsParseIntoIndexToName() throws Exception {
    MethodMetadata md = contract.parseAndValidatateMetadata(FormParams.class.getDeclaredMethod("login", String.class,
        String.class, String.class));

    assertNull(md.template().body());
    assertNull(md.template().bodyTemplate());
    assertEquals(md.formParams(), ImmutableList.of("customer_name", "user_name", "password"));
    assertEquals(md.indexToName().get(0), ImmutableSet.of("customer_name"));
    assertEquals(md.indexToName().get(1), ImmutableSet.of("user_name"));
    assertEquals(md.indexToName().get(2), ImmutableSet.of("password"));
  }

  @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "FormParam.value\\(\\) was empty on parameter 0")
  public void emptyFormParam() throws Exception {
    contract.parseAndValidatateMetadata(FormParams.class.getDeclaredMethod("emptyFormParam", String.class));
  }

  interface HeaderParams {
    @POST void logout(@HeaderParam("Auth-Token") String token);

    @GET Response emptyHeaderParam(@HeaderParam("") String empty);
  }

  @Test public void headerParamsParseIntoIndexToName() throws Exception {
    MethodMetadata md = contract.parseAndValidatateMetadata(HeaderParams.class.getDeclaredMethod("logout", String.class));

    assertEquals(md.template().headers().get("Auth-Token"), ImmutableSet.of("{Auth-Token}"));
    assertEquals(md.indexToName().get(0), ImmutableSet.of("Auth-Token"));
  }

  @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "HeaderParam.value\\(\\) was empty on parameter 0")
  public void emptyHeaderParam() throws Exception {
    contract.parseAndValidatateMetadata(HeaderParams.class.getDeclaredMethod("emptyHeaderParam", String.class));
  }
}
