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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
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

import feign.MethodMetadata;
import feign.Response;

import static feign.assertj.FeignAssertions.assertThat;
import static java.util.Arrays.asList;
import static org.assertj.core.data.MapEntry.entry;

/**
 * Tests interfaces defined per {@link JAXRSContract} are interpreted into expected {@link feign
 * .RequestTemplate template} instances.
 */
public class JAXRSContractTest {

  private static final List<String> STRING_LIST = null;
  @Rule
  public final ExpectedException thrown = ExpectedException.none();
  JAXRSContract contract = new JAXRSContract();

  @Test
  public void httpMethods() throws Exception {
    assertThat(
        contract.parseAndValidatateMetadata(Methods.class.getDeclaredMethod("post")).template())
        .hasMethod("POST");

    assertThat(
        contract.parseAndValidatateMetadata(Methods.class.getDeclaredMethod("put")).template())
        .hasMethod("PUT");

    assertThat(
        contract.parseAndValidatateMetadata(Methods.class.getDeclaredMethod("get")).template())
        .hasMethod("GET");

    assertThat(
        contract.parseAndValidatateMetadata(Methods.class.getDeclaredMethod("delete")).template())
        .hasMethod("DELETE");
  }

  @Test
  public void customMethodWithoutPath() throws Exception {
    assertThat(contract.parseAndValidatateMetadata(CustomMethod.class.getDeclaredMethod("patch"))
                   .template())
        .hasMethod("PATCH")
        .hasUrl("");
  }

  @Test
  public void queryParamsInPathExtract() throws Exception {
    assertThat(
        contract.parseAndValidatateMetadata(WithQueryParamsInPath.class.getDeclaredMethod("none"))
            .template())
        .hasUrl("/")
        .hasQueries();

    assertThat(
        contract.parseAndValidatateMetadata(WithQueryParamsInPath.class.getDeclaredMethod("one"))
            .template())
        .hasUrl("/")
        .hasQueries(
            entry("Action", asList("GetUser"))
        );

    assertThat(
        contract.parseAndValidatateMetadata(WithQueryParamsInPath.class.getDeclaredMethod("two"))
            .template())
        .hasUrl("/")
        .hasQueries(
            entry("Action", asList("GetUser")),
            entry("Version", asList("2010-05-08"))
        );

    assertThat(
        contract.parseAndValidatateMetadata(WithQueryParamsInPath.class.getDeclaredMethod("three"))
            .template())
        .hasUrl("/")
        .hasQueries(
            entry("Action", asList("GetUser")),
            entry("Version", asList("2010-05-08")),
            entry("limit", asList("1"))
        );

    assertThat(
        contract.parseAndValidatateMetadata(WithQueryParamsInPath.class.getDeclaredMethod("empty"))
            .template())
        .hasUrl("/")
        .hasQueries(
            entry("flag", asList(new String[]{null})),
            entry("Action", asList("GetUser")),
            entry("Version", asList("2010-05-08"))
        );
  }

  @Test
  public void producesAddsAcceptHeader() throws Exception {
    MethodMetadata
        md =
        contract
            .parseAndValidatateMetadata(ProducesAndConsumes.class.getDeclaredMethod("produces"));

    assertThat(md.template())
        .hasHeaders(entry("Accept", asList("application/xml")));
  }

  @Test
  public void producesNada() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Produces.value() was empty on method producesNada");

    contract
        .parseAndValidatateMetadata(ProducesAndConsumes.class.getDeclaredMethod("producesNada"));
  }

  @Test
  public void producesEmpty() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Produces.value() was empty on method producesEmpty");

    contract
        .parseAndValidatateMetadata(ProducesAndConsumes.class.getDeclaredMethod("producesEmpty"));
  }

  @Test
  public void consumesAddsContentTypeHeader() throws Exception {
    MethodMetadata
        md =
        contract
            .parseAndValidatateMetadata(ProducesAndConsumes.class.getDeclaredMethod("consumes"));

    assertThat(md.template())
        .hasHeaders(entry("Content-Type", asList("application/xml")));
  }

  @Test
  public void consumesNada() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Consumes.value() was empty on method consumesNada");

    contract
        .parseAndValidatateMetadata(ProducesAndConsumes.class.getDeclaredMethod("consumesNada"));
  }

  @Test
  public void consumesEmpty() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Consumes.value() was empty on method consumesEmpty");

    contract
        .parseAndValidatateMetadata(ProducesAndConsumes.class.getDeclaredMethod("consumesEmpty"));
  }

  @Test
  public void bodyParamIsGeneric() throws Exception {
    MethodMetadata
        md =
        contract.parseAndValidatateMetadata(BodyParams.class.getDeclaredMethod("post",
                                                                               List.class));

    assertThat(md.bodyIndex())
        .isEqualTo(0);
    assertThat(md.bodyType())
        .isEqualTo(getClass().getDeclaredField("STRING_LIST").getGenericType());
  }

  @Test
  public void tooManyBodies() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Method has too many Body");

    contract.parseAndValidatateMetadata(
        BodyParams.class.getDeclaredMethod("tooMany", List.class, List.class));
  }

  @Test
  public void emptyPathOnType() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Path.value() was empty on type ");

    contract.parseAndValidatateMetadata(EmptyPathOnType.class.getDeclaredMethod("base"));
  }

  private MethodMetadata parsePathOnTypeMethod(String name) throws NoSuchMethodException {
    return contract.parseAndValidatateMetadata(PathOnType.class.getDeclaredMethod(name));
  }

  @Test
  public void parsePathMethod() throws Exception {
    assertThat(parsePathOnTypeMethod("base").template())
        .hasUrl("/base");

    assertThat(parsePathOnTypeMethod("get").template())
        .hasUrl("/base/specific");
  }

  @Test
  public void emptyPathOnMethod() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Path.value() was empty on method emptyPath");

    parsePathOnTypeMethod("emptyPath");
  }

  @Test
  public void emptyPathParam() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("PathParam.value() was empty on parameter 0");

    contract.parseAndValidatateMetadata(
        PathOnType.class.getDeclaredMethod("emptyPathParam", String.class));
  }

  @Test
  public void pathParamWithSpaces() throws Exception {
      assertThat(contract.parseAndValidatateMetadata(
              PathOnType.class.getDeclaredMethod("pathParamWithSpaces", String.class)).template())
          .hasUrl("/base/{param}");
  }

  @Test
  public void regexPathOnMethod() throws Exception {
      assertThat(contract.parseAndValidatateMetadata(
          PathOnType.class.getDeclaredMethod("pathParamWithRegex", String.class)).template())
      .hasUrl("/base/regex/{param}");

      assertThat(contract.parseAndValidatateMetadata(
              PathOnType.class.getDeclaredMethod("pathParamWithMultipleRegex", String.class, String.class)).template())
      .hasUrl("/base/regex/{param1}/{param2}");
  }

  @Test
  public void withPathAndURIParams() throws Exception {
    MethodMetadata md = contract.parseAndValidatateMetadata(
        WithURIParam.class.getDeclaredMethod("uriParam", String.class, URI.class, String.class));

    assertThat(md.indexToName()).containsExactly(
        entry(0, asList("1")),
        // Skips 1 as it is a url index!
        entry(2, asList("2")));

    assertThat(md.urlIndex()).isEqualTo(1);
  }

  @Test
  public void pathAndQueryParams() throws Exception {
    MethodMetadata
        md =
        contract.parseAndValidatateMetadata(WithPathAndQueryParams.class.getDeclaredMethod
            ("recordsByNameAndType", int.class, String.class, String.class));

    assertThat(md.template())
        .hasQueries(entry("name", asList("{name}")), entry("type", asList("{type}")));

    assertThat(md.indexToName()).containsExactly(entry(0, asList("domainId")),
                                                 entry(1, asList("name")),
                                                 entry(2, asList("type")));
  }

  @Test
  public void emptyQueryParam() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("QueryParam.value() was empty on parameter 0");

    contract.parseAndValidatateMetadata(
        WithPathAndQueryParams.class.getDeclaredMethod("empty", String.class));
  }

  @Test
  public void formParamsParseIntoIndexToName() throws Exception {
    MethodMetadata
        md =
        contract
            .parseAndValidatateMetadata(FormParams.class.getDeclaredMethod("login", String.class,
                                                                           String.class,
                                                                           String.class));

    assertThat(md.formParams())
        .containsExactly("customer_name", "user_name", "password");

    assertThat(md.indexToName()).containsExactly(
        entry(0, asList("customer_name")),
        entry(1, asList("user_name")),
        entry(2, asList("password"))
    );
  }

  /**
   * Body type is only for the body param.
   */
  @Test
  public void formParamsDoesNotSetBodyType() throws Exception {
    MethodMetadata
        md =
        contract
            .parseAndValidatateMetadata(FormParams.class.getDeclaredMethod("login", String.class,
                                                                           String.class,
                                                                           String.class));

    assertThat(md.bodyType()).isNull();
  }

  @Test
  public void emptyFormParam() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("FormParam.value() was empty on parameter 0");

    contract.parseAndValidatateMetadata(
        FormParams.class.getDeclaredMethod("emptyFormParam", String.class));
  }

  @Test
  public void headerParamsParseIntoIndexToName() throws Exception {
    MethodMetadata md =
        contract.parseAndValidatateMetadata(
            HeaderParams.class.getDeclaredMethod("logout", String.class));

    assertThat(md.template())
        .hasHeaders(entry("Auth-Token", asList("{Auth-Token}")));

    assertThat(md.indexToName())
        .containsExactly(entry(0, asList("Auth-Token")));
  }

  @Test
  public void emptyHeaderParam() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("HeaderParam.value() was empty on parameter 0");

    contract.parseAndValidatateMetadata(
        HeaderParams.class.getDeclaredMethod("emptyHeaderParam", String.class));
  }

  @Test
  public void pathsWithoutSlashesParseCorrectly() throws Exception {
    assertThat(
        contract.parseAndValidatateMetadata(PathsWithoutAnySlashes.class.getDeclaredMethod("get"))
            .template())
        .hasUrl("/base/specific");
  }

  @Test
  public void pathsWithSomeSlashesParseCorrectly() throws Exception {
    assertThat(
        contract.parseAndValidatateMetadata(PathsWithSomeSlashes.class.getDeclaredMethod("get"))
            .template())
        .hasUrl("/base/specific");
  }

  @Test
  public void pathsWithSomeOtherSlashesParseCorrectly() throws Exception {
    assertThat(contract.parseAndValidatateMetadata(
        PathsWithSomeOtherSlashes.class.getDeclaredMethod("get")).template())
        .hasUrl("/base/specific");

  }

  @Test
  public void classWithRootPathParsesCorrectly() throws Exception {
      assertThat(
              contract.parseAndValidatateMetadata(ClassRootPath.class.getDeclaredMethod("get"))
                  .template())
              .hasUrl("/specific");
  }

  @Test
  public void classPathWithTrailingSlashParsesCorrectly() throws Exception {
      assertThat(
              contract.parseAndValidatateMetadata(ClassPathWithTrailingSlash.class.getDeclaredMethod("get"))
                  .template())
              .hasUrl("/base/specific");
  }

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

  interface CustomMethod {

    @PATCH
    Response patch();

    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @HttpMethod("PATCH")
    public @interface PATCH {

    }
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

  interface ProducesAndConsumes {

    @GET
    @Produces("application/xml")
    Response produces();

    @GET
    @Produces({})
    Response producesNada();

    @GET
    @Produces({""})
    Response producesEmpty();

    @POST
    @Consumes("application/xml")
    Response consumes();

    @POST
    @Consumes({})
    Response consumesNada();

    @POST
    @Consumes({""})
    Response consumesEmpty();
  }

  interface BodyParams {

    @POST
    Response post(List<String> body);

    @POST
    Response tooMany(List<String> body, List<String> body2);
  }

  @Path("")
  interface EmptyPathOnType {

    @GET
    Response base();
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

    @GET
    @Path("/{   param   }")
    Response pathParamWithSpaces(@PathParam("param") String path);

    @GET
    @Path("regex/{param:.+}")
    Response pathParamWithRegex(@PathParam("param") String path);

    @GET
    @Path("regex/{param1:[0-9]*}/{  param2 : .+}")
    Response pathParamWithMultipleRegex(@PathParam("param1") String param1, @PathParam("param2") String param2);
  }

  interface WithURIParam {

    @GET
    @Path("/{1}/{2}")
    Response uriParam(@PathParam("1") String one, URI endpoint, @PathParam("2") String two);
  }

  interface WithPathAndQueryParams {

    @GET
    @Path("/domains/{domainId}/records")
    Response recordsByNameAndType(@PathParam("domainId") int id,
                                  @QueryParam("name") String nameFilter,
                                  @QueryParam("type") String typeFilter);

    @GET
    Response empty(@QueryParam("") String empty);
  }

  interface FormParams {

    @POST
    void login(
        @FormParam("customer_name") String customer,
        @FormParam("user_name") String user, @FormParam("password") String password);

    @GET
    Response emptyFormParam(@FormParam("") String empty);
  }

  interface HeaderParams {

    @POST
    void logout(@HeaderParam("Auth-Token") String token);

    @GET
    Response emptyHeaderParam(@HeaderParam("") String empty);
  }

  @Path("base")
  interface PathsWithoutAnySlashes {

    @GET
    @Path("specific")
    Response get();
  }

  @Path("/base")
  interface PathsWithSomeSlashes {

    @GET
    @Path("specific")
    Response get();
  }

  @Path("base")
  interface PathsWithSomeOtherSlashes {

    @GET
    @Path("/specific")
    Response get();
  }

  @Path("/")
  interface ClassRootPath {
      @GET
      @Path("/specific")
      Response get();
  }

  @Path("/base/")
  interface ClassPathWithTrailingSlash {
      @GET
      @Path("/specific")
      Response get();
  }
}
