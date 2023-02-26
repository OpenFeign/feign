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

import feign.MethodMetadata;
import feign.Response;
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
import javax.ws.rs.core.MediaType;

/**
 * Tests interfaces defined per {@link JAXRSContract} are interpreted into expected
 * {@link feign .RequestTemplate template} instances.
 */
public class JAXRSContractTest extends JAXRSContractTestSupport<JAXRSContract> {

  protected interface Methods {

    @POST
    void post();

    @PUT
    void put();

    @GET
    void get();

    @DELETE
    void delete();
  }

  protected interface CustomMethod {

    @PATCH
    Response patch();

    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @HttpMethod("PATCH")
    public @interface PATCH {
    }
  }

  protected interface WithQueryParamsInPath {

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
  protected interface ProducesAndConsumes {

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

  protected interface BodyParams {

    @POST
    Response post(List<String> body);

    @POST
    Response tooMany(List<String> body, List<String> body2);
  }

  @Path("")
  protected interface EmptyPathOnType {

    @GET
    Response base();

    @GET
    @Path("/specific")
    Response get();
  }

  @Path("/base")
  protected interface PathOnType {

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
    Response pathParamWithMultipleRegex(
                                        @PathParam("param1") String param1,
                                        @PathParam("param2") String param2);
  }

  @Path("/{baseparam: [0-9]+}")
  protected interface ComplexPathOnType {

    @GET
    @Path("regex/{param1:[0-9]*}/{  param2 : .+}")
    Response pathParamWithMultipleRegex(
                                        @PathParam("param1") String param1,
                                        @PathParam("param2") String param2);
  }

  protected interface WithURIParam {

    @GET
    @Path("/{1}/{2}")
    Response uriParam(@PathParam("1") String one, URI endpoint, @PathParam("2") String two);
  }

  protected interface WithPathAndQueryParams {

    @GET
    @Path("/domains/{domainId}/records")
    Response recordsByNameAndType(
                                  @PathParam("domainId") int id,
                                  @QueryParam("name") String nameFilter,
                                  @QueryParam("type") String typeFilter);

    @GET
    Response empty(@QueryParam("") String empty);
  }

  protected interface FormParams {

    @POST
    void login(
               @FormParam("customer_name") String customer,
               @FormParam("user_name") String user,
               @FormParam("password") String password);

    @GET
    Response emptyFormParam(@FormParam("") String empty);
  }

  protected interface HeaderParams {

    @POST
    void logout(@HeaderParam("Auth-Token") String token);

    @GET
    Response emptyHeaderParam(@HeaderParam("") String empty);
  }

  @Path("base")
  protected interface PathsWithoutAnySlashes {

    @GET
    @Path("specific")
    Response get();
  }

  @Path("/base")
  protected interface PathsWithSomeSlashes {

    @GET
    @Path("specific")
    Response get();
  }

  @Path("base")
  protected interface PathsWithSomeOtherSlashes {

    @GET
    @Path("/specific")
    Response get();
  }

  @Path("/")
  protected interface ClassRootPath {
    @GET
    @Path("/specific")
    Response get();
  }

  @Path("/base/")
  protected interface ClassPathWithTrailingSlash {
    @GET
    @Path("/specific")
    Response get();
  }

  @Path("/base/")
  protected interface MethodWithFirstPathThenGetWithoutLeadingSlash {
    @Path("specific")
    @GET
    Response get();
  }

  protected interface MixedAnnotations {

    @GET
    @Path("/api/stuff?multiple=stuff")
    @Produces("application/json")
    Response getWithHeaders(
                            @HeaderParam("Accept") String accept,
                            @QueryParam("multiple") String multiple,
                            @QueryParam("another") String another);
  }

  @Override
  protected JAXRSContract createContract() {
    return new JAXRSContract();
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
