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

import static feign.jaxrs.JAXRSModule.ACCEPT;
import static feign.jaxrs.JAXRSModule.CONTENT_TYPE;
import static javax.ws.rs.HttpMethod.DELETE;
import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.HttpMethod.PUT;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.reflect.TypeToken;
import feign.MethodMetadata;
import feign.Response;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * Tests interfaces defined per {@link feign.jaxrs.JAXRSModule.JAXRSContract} are interpreted into
 * expected {@link feign .RequestTemplate template} instances.
 */
public class JAXRSContractTest {
  @Rule public final ExpectedException thrown = ExpectedException.none();

  JAXRSModule.JAXRSContract contract = new JAXRSModule.JAXRSContract();

  interface Methods {
    @POST
    void post();

    @PUT
    void put();

    @GET
    void get();

    @DELETE
    void delete();
  }

  @Test
  public void httpMethods() throws Exception {
    assertEquals(
        POST,
        contract
            .parseAndValidatateMetadata(Methods.class.getDeclaredMethod("post"))
            .template()
            .method());
    assertEquals(
        PUT,
        contract
            .parseAndValidatateMetadata(Methods.class.getDeclaredMethod("put"))
            .template()
            .method());
    assertEquals(
        GET,
        contract
            .parseAndValidatateMetadata(Methods.class.getDeclaredMethod("get"))
            .template()
            .method());
    assertEquals(
        DELETE,
        contract
            .parseAndValidatateMetadata(Methods.class.getDeclaredMethod("delete"))
            .template()
            .method());
  }

  interface CustomMethodAndURIParam {
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @HttpMethod("PATCH")
    public @interface PATCH {}

    @PATCH
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
    @GET
    @Path("/")
    Response none();

    @GET
    @Path("/?Action=GetUser")
    Response one();

    @GET
    @Path("/?Action=GetUser&Version=2010-05-08")
    Response two();

    @GET
    @Path("/?Action=GetUser&Version=2010-05-08&limit=1")
    Response three();

    @GET
    @Path("/?flag&Action=GetUser&Version=2010-05-08")
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

  interface ProducesAndConsumes {
    @GET
    @Produces(APPLICATION_XML)
    Response produces();

    @GET
    @Produces({})
    Response producesNada();

    @GET
    @Produces({""})
    Response producesEmpty();

    @POST
    @Consumes(APPLICATION_JSON)
    Response consumes();

    @POST
    @Consumes({})
    Response consumesNada();

    @POST
    @Consumes({""})
    Response consumesEmpty();
  }

  @Test
  public void producesAddsAcceptHeader() throws Exception {
    MethodMetadata md =
        contract.parseAndValidatateMetadata(
            ProducesAndConsumes.class.getDeclaredMethod("produces"));
    assertEquals(Arrays.asList(APPLICATION_XML), md.template().headers().get(ACCEPT));
  }

  @Test
  public void producesNada() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Produces.value() was empty on method producesNada");

    contract.parseAndValidatateMetadata(
        ProducesAndConsumes.class.getDeclaredMethod("producesNada"));
  }

  @Test
  public void producesEmpty() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Produces.value() was empty on method producesEmpty");

    contract.parseAndValidatateMetadata(
        ProducesAndConsumes.class.getDeclaredMethod("producesEmpty"));
  }

  @Test
  public void consumesAddsContentTypeHeader() throws Exception {
    MethodMetadata md =
        contract.parseAndValidatateMetadata(
            ProducesAndConsumes.class.getDeclaredMethod("consumes"));
    assertEquals(Arrays.asList(APPLICATION_JSON), md.template().headers().get(CONTENT_TYPE));
  }

  @Test
  public void consumesNada() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Consumes.value() was empty on method consumesNada");

    contract.parseAndValidatateMetadata(
        ProducesAndConsumes.class.getDeclaredMethod("consumesNada"));
  }

  @Test
  public void consumesEmpty() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Consumes.value() was empty on method consumesEmpty");

    contract.parseAndValidatateMetadata(
        ProducesAndConsumes.class.getDeclaredMethod("consumesEmpty"));
  }

  interface BodyParams {
    @POST
    Response post(List<String> body);

    @POST
    Response tooMany(List<String> body, List<String> body2);
  }

  @Test
  public void bodyParamIsGeneric() throws Exception {
    MethodMetadata md =
        contract.parseAndValidatateMetadata(BodyParams.class.getDeclaredMethod("post", List.class));
    assertNull(md.template().body());
    assertNull(md.template().bodyTemplate());
    assertNull(md.urlIndex());
    assertEquals(Integer.valueOf(0), md.bodyIndex());
    assertEquals(new TypeToken<List<String>>() {}.getType(), md.bodyType());
  }

  @Test
  public void tooManyBodies() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Method has too many Body");

    contract.parseAndValidatateMetadata(
        BodyParams.class.getDeclaredMethod("tooMany", List.class, List.class));
  }

  @Path("")
  interface EmptyPathOnType {
    @GET
    Response base();
  }

  @Test
  public void emptyPathOnType() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Path.value() was empty on type ");

    contract.parseAndValidatateMetadata(EmptyPathOnType.class.getDeclaredMethod("base"));
  }

  @Path("/base")
  interface PathOnType {
    @GET
    Response base();

    @GET
    @Path("/specific")
    Response get();

    @GET
    @Path("")
    Response emptyPath();

    @GET
    @Path("/{param}")
    Response emptyPathParam(@PathParam("") String empty);
  }

  @Test
  public void pathOnType() throws Exception {
    MethodMetadata md =
        contract.parseAndValidatateMetadata(PathOnType.class.getDeclaredMethod("base"));
    assertEquals("/base", md.template().url());
    md = contract.parseAndValidatateMetadata(PathOnType.class.getDeclaredMethod("get"));
    assertEquals("/base/specific", md.template().url());
  }

  @Test
  public void emptyPathOnMethod() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Path.value() was empty on method emptyPath");

    contract.parseAndValidatateMetadata(PathOnType.class.getDeclaredMethod("emptyPath"));
  }

  @Test
  public void emptyPathParam() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("PathParam.value() was empty on parameter 0");

    contract.parseAndValidatateMetadata(
        PathOnType.class.getDeclaredMethod("emptyPathParam", String.class));
  }

  interface WithURIParam {
    @GET
    @Path("/{1}/{2}")
    Response uriParam(@PathParam("1") String one, URI endpoint, @PathParam("2") String two);
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
    @GET
    @Path("/domains/{domainId}/records")
    Response recordsByNameAndType(
        @PathParam("domainId") int id,
        @QueryParam("name") String nameFilter,
        @QueryParam("type") String typeFilter);

    @GET
    Response emptyQueryParam(@QueryParam("") String empty);
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

  @Test
  public void emptyQueryParam() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("QueryParam.value() was empty on parameter 0");

    contract.parseAndValidatateMetadata(
        WithPathAndQueryParams.class.getDeclaredMethod("emptyQueryParam", String.class));
  }

  interface FormParams {
    @POST
    void login(
        @FormParam("customer_name") String customer,
        @FormParam("user_name") String user,
        @FormParam("password") String password);

    @GET
    Response emptyFormParam(@FormParam("") String empty);
  }

  @Test
  public void formParamsParseIntoIndexToName() throws Exception {
    MethodMetadata md =
        contract.parseAndValidatateMetadata(
            FormParams.class.getDeclaredMethod("login", String.class, String.class, String.class));

    assertNull(md.template().body());
    assertNull(md.template().bodyTemplate());
    assertEquals(Arrays.asList("customer_name", "user_name", "password"), md.formParams());
    assertEquals(Arrays.asList("customer_name"), md.indexToName().get(0));
    assertEquals(Arrays.asList("user_name"), md.indexToName().get(1));
    assertEquals(Arrays.asList("password"), md.indexToName().get(2));
  }

  @Test
  public void emptyFormParam() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("FormParam.value() was empty on parameter 0");

    contract.parseAndValidatateMetadata(
        FormParams.class.getDeclaredMethod("emptyFormParam", String.class));
  }

  interface HeaderParams {
    @POST
    void logout(@HeaderParam("Auth-Token") String token);

    @GET
    Response emptyHeaderParam(@HeaderParam("") String empty);
  }

  @Test
  public void headerParamsParseIntoIndexToName() throws Exception {
    MethodMetadata md =
        contract.parseAndValidatateMetadata(
            HeaderParams.class.getDeclaredMethod("logout", String.class));

    assertEquals(Arrays.asList("{Auth-Token}"), md.template().headers().get("Auth-Token"));
    assertEquals(Arrays.asList("Auth-Token"), md.indexToName().get(0));
  }

  @Test
  public void emptyHeaderParam() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("HeaderParam.value() was empty on parameter 0");

    contract.parseAndValidatateMetadata(
        HeaderParams.class.getDeclaredMethod("emptyHeaderParam", String.class));
  }

  @Path("base")
  interface PathsWithoutAnySlashes {
    @GET
    @Path("specific")
    Response get();
  }

  @Test
  public void pathsWithoutSlashesParseCorrectly() throws Exception {
    MethodMetadata md =
        contract.parseAndValidatateMetadata(PathsWithoutAnySlashes.class.getDeclaredMethod("get"));
    assertEquals("/base/specific", md.template().url());
  }

  @Path("/base")
  interface PathsWithSomeSlashes {
    @GET
    @Path("specific")
    Response get();
  }

  @Test
  public void pathsWithSomeSlashesParseCorrectly() throws Exception {
    MethodMetadata md =
        contract.parseAndValidatateMetadata(PathsWithSomeSlashes.class.getDeclaredMethod("get"));
    assertEquals("/base/specific", md.template().url());
  }

  @Path("base")
  interface PathsWithSomeOtherSlashes {
    @GET
    @Path("/specific")
    Response get();
  }

  @Test
  public void pathsWithSomeOtherSlashesParseCorrectly() throws Exception {
    MethodMetadata md =
        contract.parseAndValidatateMetadata(
            PathsWithSomeOtherSlashes.class.getDeclaredMethod("get"));
    assertEquals("/base/specific", md.template().url());
  }
}
