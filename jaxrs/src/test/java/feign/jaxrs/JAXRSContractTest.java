/*
 * Copyright 2012-2022 The Feign Authors
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
package feign.jaxrs;

import static feign.assertj.FeignAssertions.assertThat;
import static java.util.Arrays.asList;
import static org.assertj.core.data.MapEntry.entry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import java.lang.annotation.*;
import java.net.URI;
import java.util.*;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import feign.MethodMetadata;
import feign.Response;

/**
 * Tests interfaces defined per {@link JAXRSContract} are interpreted into expected
 * {@link feign .RequestTemplate template} instances.
 */
public class JAXRSContractTest {

  private static final List<String> STRING_LIST = null;
  @Rule
  public final ExpectedException thrown = ExpectedException.none();
  JAXRSContract contract = createContract();

  protected JAXRSContract createContract() {
    return new JAXRSContract();
  }

  @Test
  public void httpMethods() throws Exception {
    assertThat(parseAndValidateMetadata(Methods.class, "post").template())
        .hasMethod("POST");

    assertThat(parseAndValidateMetadata(Methods.class, "put").template())
        .hasMethod("PUT");

    assertThat(parseAndValidateMetadata(Methods.class, "get").template())
        .hasMethod("GET");

    assertThat(parseAndValidateMetadata(Methods.class, "delete").template())
        .hasMethod("DELETE");
  }

  @Test
  public void customMethodWithoutPath() throws Exception {
    assertThat(parseAndValidateMetadata(CustomMethod.class, "patch").template())
        .hasMethod("PATCH")
        .hasUrl("/");
  }

  @Test
  public void queryParamsInPathExtract() throws Exception {
    assertThat(parseAndValidateMetadata(WithQueryParamsInPath.class, "none").template())
        .hasPath("/")
        .hasQueries();

    assertThat(parseAndValidateMetadata(WithQueryParamsInPath.class, "one").template())
        .hasPath("/")
        .hasQueries(
            entry("Action", asList("GetUser")));

    assertThat(parseAndValidateMetadata(WithQueryParamsInPath.class, "two").template())
        .hasPath("/")
        .hasQueries(
            entry("Action", asList("GetUser")),
            entry("Version", asList("2010-05-08")));

    assertThat(parseAndValidateMetadata(WithQueryParamsInPath.class, "three").template())
        .hasPath("/")
        .hasQueries(
            entry("Action", asList("GetUser")),
            entry("Version", asList("2010-05-08")),
            entry("limit", asList("1")));

    assertThat(parseAndValidateMetadata(WithQueryParamsInPath.class, "empty").template())
        .hasPath("/")
        .hasQueries(
            entry("flag", new ArrayList<>()),
            entry("Action", asList("GetUser")),
            entry("Version", asList("2010-05-08")));
  }

  @Test
  public void producesAddsAcceptHeader() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(ProducesAndConsumes.class, "produces");

    /* multiple @Produces annotations should be additive */
    assertThat(md.template())
        .hasHeaders(
            entry("Content-Type", asList("application/json")),
            entry("Accept", asList("application/xml")));
  }

  @Test
  public void producesMultipleAddsAcceptHeader() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(ProducesAndConsumes.class, "producesMultiple");

    assertThat(md.template())
        .hasHeaders(
            entry("Content-Type", Collections.singletonList("application/json")),
            entry("Accept", asList("application/xml", "text/plain")));
  }

  @Test
  public void producesNada() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Produces.value() was empty on ProducesAndConsumes#producesNada");

    parseAndValidateMetadata(ProducesAndConsumes.class, "producesNada");
  }

  @Test
  public void producesEmpty() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Produces.value() was empty on ProducesAndConsumes#producesEmpty");

    parseAndValidateMetadata(ProducesAndConsumes.class, "producesEmpty");
  }

  @Test
  public void consumesAddsContentTypeHeader() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(ProducesAndConsumes.class, "consumes");

    /* multiple @Consumes annotations are additive */
    assertThat(md.template())
        .hasHeaders(
            entry("Content-Type", asList("application/xml")),
            entry("Accept", asList("text/html")));
  }

  @Test
  public void consumesMultipleAddsContentTypeHeader() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(ProducesAndConsumes.class, "consumesMultiple");

    assertThat(md.template())
        .hasHeaders(entry("Content-Type", asList("application/xml")),
            entry("Accept", Collections.singletonList("text/html")));
  }

  @Test
  public void consumesNada() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Consumes.value() was empty on ProducesAndConsumes#consumesNada");

    parseAndValidateMetadata(ProducesAndConsumes.class, "consumesNada");
  }

  @Test
  public void consumesEmpty() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Consumes.value() was empty on ProducesAndConsumes#consumesEmpty");

    parseAndValidateMetadata(ProducesAndConsumes.class, "consumesEmpty");
  }

  @Test
  public void producesAndConsumesOnClassAddsHeader() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(ProducesAndConsumes.class, "producesAndConsumes");

    assertThat(md.template())
        .hasHeaders(entry("Content-Type", asList("application/json")),
            entry("Accept", asList("text/html")));
  }

  @Test
  public void bodyParamIsGeneric() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(BodyParams.class, "post", List.class);

    assertThat(md.bodyIndex())
        .isEqualTo(0);
    assertThat(md.bodyType())
        .isEqualTo(JAXRSContractTest.class.getDeclaredField("STRING_LIST").getGenericType());
  }

  @Test
  public void tooManyBodies() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Method has too many Body");

    parseAndValidateMetadata(BodyParams.class, "tooMany", List.class, List.class);
  }

  @Test
  public void emptyPathOnType() throws Exception {
    assertThat(parseAndValidateMetadata(EmptyPathOnType.class, "base").template())
        .hasUrl("/");
  }

  @Test
  public void emptyPathOnTypeSpecific() throws Exception {
    assertThat(parseAndValidateMetadata(EmptyPathOnType.class, "get").template())
        .hasUrl("/specific");
  }

  @Test
  public void parsePathMethod() throws Exception {
    assertThat(parseAndValidateMetadata(PathOnType.class, "base").template())
        .hasUrl("/base");

    assertThat(parseAndValidateMetadata(PathOnType.class, "get").template())
        .hasUrl("/base/specific");
  }

  @Test
  public void emptyPathOnMethod() throws Exception {
    assertThat(parseAndValidateMetadata(PathOnType.class, "emptyPath").template())
        .hasUrl("/base");
  }

  @Test
  public void emptyPathParam() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("PathParam.value() was empty on parameter 0");

    parseAndValidateMetadata(PathOnType.class, "emptyPathParam", String.class);
  }

  @Test
  public void pathParamWithSpaces() throws Exception {
    assertThat(parseAndValidateMetadata(
        PathOnType.class, "pathParamWithSpaces", String.class).template())
            .hasUrl("/base/{param}");
  }

  @Test
  public void regexPathOnMethodOrType() throws Exception {
    assertThat(parseAndValidateMetadata(
        PathOnType.class, "pathParamWithRegex", String.class).template())
            .hasUrl("/base/regex/{param}");

    assertThat(parseAndValidateMetadata(
        PathOnType.class, "pathParamWithMultipleRegex", String.class, String.class).template())
            .hasUrl("/base/regex/{param1}/{param2}");

    assertThat(parseAndValidateMetadata(
        ComplexPathOnType.class, "pathParamWithMultipleRegex", String.class, String.class)
            .template())
                .hasUrl("/{baseparam}/regex/{param1}/{param2}");
  }

  @Test
  public void withPathAndURIParams() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(WithURIParam.class,
        "uriParam", String.class, URI.class, String.class);

    assertThat(md.indexToName()).containsExactly(
        entry(0, asList("1")),
        // Skips 1 as it is a url index!
        entry(2, asList("2")));

    assertThat(md.urlIndex()).isEqualTo(1);
  }

  @Test
  public void pathAndQueryParams() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(WithPathAndQueryParams.class,
            "recordsByNameAndType", int.class, String.class, String.class);

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

    parseAndValidateMetadata(WithPathAndQueryParams.class, "empty", String.class);
  }

  @Test
  public void formParamsParseIntoIndexToName() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(FormParams.class,
        "login", String.class, String.class, String.class);

    assertThat(md.formParams())
        .containsExactly("customer_name", "user_name", "password");

    assertThat(md.indexToName()).containsExactly(
        entry(0, asList("customer_name")),
        entry(1, asList("user_name")),
        entry(2, asList("password")));
  }

  /**
   * Body type is only for the body param.
   */
  @Test
  public void formParamsDoesNotSetBodyType() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(FormParams.class,
        "login", String.class, String.class, String.class);

    assertThat(md.bodyType()).isNull();
  }

  @Test
  public void emptyFormParam() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("FormParam.value() was empty on parameter 0");

    parseAndValidateMetadata(FormParams.class, "emptyFormParam", String.class);
  }

  @Test
  public void headerParamsParseIntoIndexToName() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(HeaderParams.class, "logout", String.class);

    assertThat(md.template())
        .hasHeaders(entry("Auth-Token", asList("{Auth-Token}")));

    assertThat(md.indexToName())
        .containsExactly(entry(0, asList("Auth-Token")));
  }

  @Test
  public void emptyHeaderParam() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("HeaderParam.value() was empty on parameter 0");

    parseAndValidateMetadata(HeaderParams.class, "emptyHeaderParam", String.class);
  }

  @Test
  public void pathsWithoutSlashesParseCorrectly() throws Exception {
    assertThat(parseAndValidateMetadata(PathsWithoutAnySlashes.class, "get").template())
        .hasUrl("/base/specific");
  }

  @Test
  public void pathsWithSomeSlashesParseCorrectly() throws Exception {
    assertThat(parseAndValidateMetadata(PathsWithSomeSlashes.class, "get").template())
        .hasUrl("/base/specific");
  }

  @Test
  public void pathsWithSomeOtherSlashesParseCorrectly() throws Exception {
    assertThat(parseAndValidateMetadata(PathsWithSomeOtherSlashes.class, "get").template())
        .hasUrl("/base/specific");
  }

  @Test
  public void classWithRootPathParsesCorrectly() throws Exception {
    assertThat(parseAndValidateMetadata(ClassRootPath.class, "get").template())
        .hasUrl("/specific");
  }

  @Test
  public void classPathWithTrailingSlashParsesCorrectly() throws Exception {
    assertThat(parseAndValidateMetadata(ClassPathWithTrailingSlash.class, "get").template())
        .hasUrl("/base/specific");
  }

  @Test
  public void methodPathWithoutLeadingSlashParsesCorrectly() throws Exception {
    assertThat(parseAndValidateMetadata(MethodWithFirstPathThenGetWithoutLeadingSlash.class, "get")
        .template())
            .hasUrl("/base/specific");
  }


  @Test
  public void producesWithHeaderParamContainAllHeaders() throws Exception {
    assertThat(parseAndValidateMetadata(MixedAnnotations.class, "getWithHeaders",
        String.class, String.class, String.class)
            .template())
                .hasHeaders(entry("Accept", Arrays.asList("application/json", "{Accept}")))
                .hasQueries(
                    entry("multiple", Arrays.asList("stuff", "{multiple}")),
                    entry("another", Collections.singletonList("{another}")));
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

  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_HTML)
  interface ProducesAndConsumes {

    @GET
    @Produces("application/xml")
    Response produces();

    @GET
    @Produces({"application/xml", "text/plain"})
    Response producesMultiple();

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
    @Consumes({"application/xml", "application/json"})
    Response consumesMultiple();

    @POST
    @Consumes({})
    Response consumesNada();

    @POST
    @Consumes({""})
    Response consumesEmpty();

    @POST
    Response producesAndConsumes();
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

    @GET
    @Path("/specific")
    Response get();
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
    Response pathParamWithMultipleRegex(@PathParam("param1") String param1,
                                        @PathParam("param2") String param2);
  }

  @Path("/{baseparam: [0-9]+}")
  interface ComplexPathOnType {

    @GET
    @Path("regex/{param1:[0-9]*}/{  param2 : .+}")
    Response pathParamWithMultipleRegex(@PathParam("param1") String param1,
                                        @PathParam("param2") String param2);
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
               @FormParam("user_name") String user,
               @FormParam("password") String password);

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

  @Path("/base/")
  interface MethodWithFirstPathThenGetWithoutLeadingSlash {
    @Path("specific")
    @GET
    Response get();
  }

  protected MethodMetadata parseAndValidateMetadata(Class<?> targetType,
                                                    String method,
                                                    Class<?>... parameterTypes)
      throws NoSuchMethodException {
    return contract.parseAndValidateMetadata(targetType,
        targetType.getMethod(method, parameterTypes));
  }

  interface MixedAnnotations {

    @GET
    @Path("/api/stuff?multiple=stuff")
    @Produces("application/json")
    Response getWithHeaders(@HeaderParam("Accept") String accept,
                            @QueryParam("multiple") String multiple,
                            @QueryParam("another") String another);
  }
}
