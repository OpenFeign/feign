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
package feign.jaxrs;

import static feign.assertj.FeignAssertions.assertThat;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import feign.MethodMetadata;
import feign.Response;
import feign.jaxrs.JAXRS3ContractTest.JakartaInternals.BeanParamInput;
import feign.jaxrs3.JAXRS3Contract;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

/**
 * Tests interfaces defined per {@link JAXRS3Contract} are interpreted into expected
 * {@link feign .RequestTemplate template} instances.
 */
class JAXRS3ContractTest extends JAXRSContractTestSupport<JAXRS3Contract> {

  @Test
  void injectJaxrsInternals() throws Exception {
    final MethodMetadata methodMetadata =
        parseAndValidateMetadata(JakartaInternals.class, "inject", AsyncResponse.class,
            UriInfo.class);
    assertThat(methodMetadata.template())
        .noRequestBody();
  }

  @Test
  void injectBeanParam() throws Exception {
    final MethodMetadata methodMetadata =
        parseAndValidateMetadata(JakartaInternals.class, "beanParameters", BeanParamInput.class);
    assertThat(methodMetadata.template())
        .noRequestBody();

    assertThat(methodMetadata.template())
        .hasHeaders(entry("X-Custom-Header", asList("{X-Custom-Header}")));
    assertThat(methodMetadata.template())
        .hasQueries(entry("query", asList("{query}")));
    assertThat(methodMetadata.formParams())
        .isNotEmpty()
        .containsExactly("form");

  }

  public interface JakartaInternals {
    @GET
    @Path("/")
    void inject(@Suspended AsyncResponse ar, @Context UriInfo info);

    @Path("/{path}")
    @POST
    void beanParameters(@BeanParam BeanParamInput beanParam);

    public class BeanParamInput {

      @PathParam("path")
      String path;

      @QueryParam("query")
      String query;

      @FormParam("form")
      String form;

      @HeaderParam("X-Custom-Header")
      String header;
    }
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
    Response emptyPathParam(@PathParam(value = "") String empty);

    @GET
    @Path("/{   param   }")
    Response pathParamWithSpaces(@PathParam("param") String path);

    @GET
    @Path("regex/{param:.+}")
    Response pathParamWithRegex(@PathParam("param") String path);

    @GET
    @Path("regex/{param1:[0-9]*}/{  param2 : .+}")
    Response pathParamWithMultipleRegex(
                                        @PathParam("param1") String param1,
                                        @PathParam("param2") String param2);
  }

  @Path("/{baseparam: [0-9]+}")
  interface ComplexPathOnType {

    @GET
    @Path("regex/{param1:[0-9]*}/{  param2 : .+}")
    Response pathParamWithMultipleRegex(
                                        @PathParam("param1") String param1,
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
    Response recordsByNameAndType(
                                  @PathParam("domainId") int id,
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

  interface MixedAnnotations {

    @GET
    @Path("/api/stuff?multiple=stuff")
    @Produces("application/json")
    Response getWithHeaders(
                            @HeaderParam("Accept") String accept,
                            @QueryParam("multiple") String multiple,
                            @QueryParam("another") String another);
  }

  @Override
  protected JAXRS3Contract createContract() {
    return new JAXRS3Contract();
  }

  @Override
  protected MethodMetadata parseAndValidateMetadata(
                                                    Class<?> targetType,
                                                    String method,
                                                    Class<?>... parameterTypes)
      throws NoSuchMethodException {
    return contract.parseAndValidateMetadata(
        targetType, targetType.getMethod(method, parameterTypes));
  }

  @Override
  protected Class<?> methodsClass() {
    return Methods.class;
  }

  @Override
  protected Class<?> customMethodClass() {
    return CustomMethod.class;
  }

  @Override
  protected Class<?> withQueryParamsInPathClass() {
    return WithQueryParamsInPath.class;
  }

  @Override
  protected Class<?> producesAndConsumesClass() {
    return ProducesAndConsumes.class;
  }

  @Override
  protected Class<?> bodyParamsClass() {
    return BodyParams.class;
  }

  @Override
  protected Class<?> emptyPathOnTypeClass() {
    return EmptyPathOnType.class;
  }

  @Override
  protected Class<?> pathOnTypeClass() {
    return PathOnType.class;
  }

  @Override
  protected Class<?> complexPathOnTypeClass() {
    return ComplexPathOnType.class;
  }

  @Override
  protected Class<?> withURIParamClass() {
    return WithURIParam.class;
  }

  @Override
  protected Class<?> withPathAndQueryParamsClass() {
    return WithPathAndQueryParams.class;
  }

  @Override
  protected Class<?> formParamsClass() {
    return FormParams.class;
  }

  @Override
  protected Class<?> headerParamsClass() {
    return HeaderParams.class;
  }

  @Override
  protected Class<?> pathsWithoutAnySlashesClass() {
    return PathsWithoutAnySlashes.class;
  }

  @Override
  protected Class<?> pathsWithSomeSlashesClass() {
    return PathsWithSomeSlashes.class;
  }

  @Override
  protected Class<?> pathsWithSomeOtherSlashesClass() {
    return PathsWithSomeOtherSlashes.class;
  }

  @Override
  protected Class<?> classRootPathClass() {
    return ClassRootPath.class;
  }

  @Override
  protected Class<?> classPathWithTrailingSlashClass() {
    return ClassPathWithTrailingSlash.class;
  }

  @Override
  protected Class<?> methodWithFirstPathThenGetWithoutLeadingSlashClass() {
    return MethodWithFirstPathThenGetWithoutLeadingSlash.class;
  }

  @Override
  protected Class<?> mixedAnnotationsClass() {
    return MixedAnnotations.class;
  }
}
