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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.util.List;
import feign.MethodMetadata;
import feign.Response;
import feign.jaxrs.JAXRSContractTest.BaseCookieParams;
import feign.jaxrs.JakartaContractTest.JakartaInternals.Input;
import feign.jaxrs.JakartaContractTest.JakartaInternalsRecord.InputRecord;
import feign.jaxrs2.JAXRS2ContractTestSupport;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.Encoded;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.MatrixParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;

/**
 * Tests interfaces defined per {@link JakartaContract} are interpreted into expected
 * {@link feign .RequestTemplate template} instances.
 */
public final class JakartaContractTest extends JAXRS2ContractTestSupport<JakartaContract> {


  @Override
  protected JakartaContract createContract() {
    return new JakartaContract();
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

  interface Methods extends JAXRSContractTest.Methods {
    @Override
    @POST
    void post();

    @Override
    @PUT
    void put();

    @Override
    @GET
    void get();

    @Override
    @DELETE
    void delete();
  }

  interface CustomMethod extends JAXRSContractTest.CustomMethod {
    @Override
    @PATCH
    Response patch();

    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @HttpMethod("PATCH")
    public @interface PATCH {
    }
  }

  interface WithQueryParamsInPath extends JAXRSContractTest.WithQueryParamsInPath {
    @Override
    @GET
    @Path("/")
    Response none();

    @Override
    @GET
    @Path("/?Action=GetUser")
    Response one();

    @Override
    @GET
    @Path("/?Action=GetUser&Version=2010-05-08")
    Response two();

    @Override
    @GET
    @Path("/?Action=GetUser&Version=2010-05-08&limit=1")
    Response three();

    @Override
    @GET
    @Path("/?flag&Action=GetUser&Version=2010-05-08")
    Response empty();
  }

  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_HTML)
  interface ProducesAndConsumes extends JAXRSContractTest.ProducesAndConsumes {
    @Override
    @GET
    @Produces("application/xml")
    Response produces();

    @Override
    @GET
    @Produces({"application/xml", "text/plain"})
    Response producesMultiple();

    @Override
    @GET
    @Produces({})
    Response producesNada();

    @Override
    @GET
    @Produces({""})
    Response producesEmpty();

    @Override
    @POST
    @Consumes("application/xml")
    Response consumes();

    @Override
    @POST
    @Consumes({"application/xml", "application/json"})
    Response consumesMultiple();

    @Override
    @POST
    @Consumes({})
    Response consumesNada();

    @Override
    @POST
    @Consumes({""})
    Response consumesEmpty();

    @Override
    @POST
    Response producesAndConsumes();
  }

  interface BodyParams extends JAXRSContractTest.BodyParams {
    @Override
    @POST
    Response post(List<String> body);

    @Override
    @POST
    Response tooMany(List<String> body, List<String> body2);
  }

  @Path("")
  interface EmptyPathOnType extends JAXRSContractTest.EmptyPathOnType {
    @Override
    @GET
    Response base();

    @Override
    @GET
    @Path("/specific")
    Response get();
  }

  @Path("/base")
  interface PathOnType extends JAXRSContractTest.PathOnType {
    @Override
    @GET
    Response base();

    @Override
    @GET
    @Path("/specific")
    Response get();

    @Override
    @GET
    @Path("")
    Response emptyPath();

    @Override
    @GET
    @Path("/{param}")
    Response emptyPathParam(@PathParam("") String empty);

    @Override
    @GET
    @Path("/{   param   }")
    Response pathParamWithSpaces(@PathParam("param") String path);

    @Override
    @GET
    @Path("regex/{param:.+}")
    Response pathParamWithRegex(@PathParam("param") String path);

    @Override
    @GET
    @Path("regex/{param1:[0-9]*}/{  param2 : .+}")
    Response pathParamWithMultipleRegex(@PathParam("param1") String param1,
                                        @PathParam("param2") String param2);
  }

  @Path("/{baseparam: [0-9]+}")
  interface ComplexPathOnType extends JAXRSContractTest.ComplexPathOnType {
    @Override
    @GET
    @Path("regex/{param1:[0-9]*}/{  param2 : .+}")
    Response pathParamWithMultipleRegex(@PathParam("param1") String param1,
                                        @PathParam("param2") String param2);
  }

  interface WithURIParam extends JAXRSContractTest.WithURIParam {
    @Override
    @GET
    @Path("/{1}/{2}")
    Response uriParam(@PathParam("1") String one, URI endpoint, @PathParam("2") String two);
  }

  interface WithPathAndQueryParams extends JAXRSContractTest.WithPathAndQueryParams {
    @Override
    @GET
    @Path("/domains/{domainId}/records")
    Response recordsByNameAndType(@PathParam("domainId") int id,
                                  @QueryParam("name") String nameFilter,
                                  @QueryParam("type") String typeFilter);

    @Override
    @GET
    Response empty(@QueryParam("") String empty);
  }

  interface FormParams extends JAXRSContractTest.FormParams {
    @Override
    @POST
    void login(@FormParam("customer_name") String customer,
               @FormParam("user_name") String user,
               @FormParam("password") String password);

    @Override
    @GET
    Response emptyFormParam(@FormParam("") String empty);
  }

  interface HeaderParams extends JAXRSContractTest.HeaderParams {

    @Override
    @POST
    void logout(@HeaderParam("Auth-Token") String token);

    @Override
    @GET
    Response emptyHeaderParam(@HeaderParam("") String empty);
  }

  @Path("base")
  interface PathsWithoutAnySlashes extends JAXRSContractTest.PathsWithoutAnySlashes {

    @GET
    @Path("specific")
    Response get();
  }

  @Path("/base")
  interface PathsWithSomeSlashes extends JAXRSContractTest.PathsWithSomeSlashes {

    @GET
    @Path("specific")
    Response get();
  }

  @Path("base")
  interface PathsWithSomeOtherSlashes extends JAXRSContractTest.PathsWithSomeOtherSlashes {

    @GET
    @Path("/specific")
    Response get();
  }

  @Path("/")
  interface ClassRootPath extends JAXRSContractTest.ClassRootPath {
    @GET
    @Path("/specific")
    Response get();
  }

  @Path("/base/")
  interface ClassPathWithTrailingSlash extends JAXRSContractTest.ClassPathWithTrailingSlash {
    @GET
    @Path("/specific")
    Response get();
  }

  @Path("/base/")
  interface MethodWithFirstPathThenGetWithoutLeadingSlash
      extends JAXRSContractTest.MethodWithFirstPathThenGetWithoutLeadingSlash {
    @Path("specific")
    @GET
    Response get();
  }

  interface MixedAnnotations extends JAXRSContractTest.MixedAnnotations {

    @GET
    @Path("/api/stuff?multiple=stuff")
    @Produces("application/json")
    Response getWithHeaders(@HeaderParam("Accept") String accept,
                            @QueryParam("multiple") String multiple,
                            @QueryParam("another") String another);
  }

  interface DefaultValues extends JAXRSContractTest.DefaultValues {

    @Override
    @GET
    @Path("/{1}/{2}")
    Response path(@PathParam("1") @DefaultValue("defaultPath1") String one,
                  URI endpoint,
                  @PathParam("2") @DefaultValue("defaultPath2") String two);

    @GET
    @Path("/domains/{domainId}/records")
    Response query(@PathParam("domainId") int id,
                   @QueryParam("name") @DefaultValue("defaultName") String nameFilter,
                   @QueryParam("type") @DefaultValue("defaultType") String typeFilter);

    @POST
    void header(@HeaderParam("Auth-Token") @DefaultValue("defaultToken") String token);

    @GET
    Response cookie(@CookieParam("cookie1") @DefaultValue("defaultCookie1Value") String cookie1,
                    @CookieParam("cookie2") @DefaultValue("defaultCookie2Value") String cookie2);

    @POST
    void form(@FormParam("customer_name") @DefaultValue("netflix") String customer,
              @FormParam("user_name") @DefaultValue("denominator") String user,
              @FormParam("password") @DefaultValue("password") String password);

  }

  private interface MatrixParams extends JAXRSContractTest.MatrixParams {

    @GET
    @Path("/{year};author={author};country={country}/")
    Response singlePath(@PathParam("year") String year,
                        @MatrixParam("author") @DefaultValue("defaultAuthor") String author,
                        @MatrixParam("country") @DefaultValue("defaultCountry") String country,
                        @QueryParam("category") String category

    );

    @GET
    @Path("/res/{path1};name={name}/{path2};country={country}/")
    Response multiPath(@PathParam("path1") String person,
                       @MatrixParam("name") @DefaultValue("tom") String name,
                       @PathParam("path2") String location,
                       @MatrixParam("country") @DefaultValue("US") String country,
                       @QueryParam("page") int page);
  }

  interface EmptyCookieParam extends JAXRSContractTest.EmptyCookieParam {
    @GET
    Response empty(@CookieParam("") String cookie);
  }


  interface CookieParams extends JAXRSContractTest.BaseCookieParams<Cookie> {

    @Override
    @Path("/res")
    @GET
    void withString(@CookieParam("cookie1") String cookie1, @CookieParam("cookie2") String cookie2);

    @Override
    @Path("/res")
    @GET
    Response withType(@CookieParam("cookie1") Cookie cookie,
                      @CookieParam("cookie2") Cookie cookie2);

  }

  @Encoded
  interface EncodedType extends JAXRSContractTest.EncodedType {
    @GET
    @Path("/{path}")
    Response get(@PathParam("path") String path);

    @POST
    @Path("/{path}")
    Response post(@PathParam("path") String path);
  }

  interface EncodedMethodAndParam extends JAXRSContractTest.EncodedMethodAndParam {
    @GET
    @Path("/{path}")
    @Encoded
    Response method(@PathParam("path") String path,
                    @QueryParam("encodedParam") String encodedParam);

    @GET
    @Path("/{path}")
    Response methodNotAnnotated(@PathParam("path") String path,
                                @QueryParam("encodedParam") String encodedParam);

    @GET
    @Path("/{encodedParam}")
    Response pathParam(@Encoded @PathParam("encodedParam") String encodedParam);

    @GET
    @Path("/{encodedParam}")
    Response pathParamNotAnnotated(@PathParam("encodedParam") String encodedParam);

    @GET
    @Path("/res")
    Response queryParam(@Encoded @QueryParam("encodedParam") String encodedParam);

    @GET
    @Path("/res")
    Response queryParamNotAnnotated(@QueryParam("encodedParam") String encodedParam);

    @POST
    @Path("/")
    Response formParam(@Encoded @FormParam("encodedParam") String encodedParam);

    @POST
    @Path("/")
    Response formParamNotAnnotated(@FormParam("encodedParam") String encodedParam);

    @GET
    @Path("/res;author={author};country={country}/")
    Response matrixParam(@Encoded @MatrixParam("author") String author,
                         @Encoded @MatrixParam("country") String country,
                         @QueryParam("category") String category);

    @GET
    @Path("/res;author={author};country={country}/")
    Response matrixParamNotAnnotated(@MatrixParam("author") String author,
                                     @MatrixParam("country") String country,
                                     @QueryParam("category") String category);
  }

  @Path("/{path}")
  public interface JakartaInternals extends BaseJaxrs2Internals<Input, Cookie> {

    @Override
    @POST
    void beanParameters(@BeanParam Input input);

    class Input {

      @DefaultValue("defaultPath")
      @PathParam("path")
      private String path;

      @Encoded
      @DefaultValue("defaultForm")
      @FormParam("form")
      private String form;

      @DefaultValue("defaultHeader")
      @HeaderParam("X-Custom-Header")
      private String header;

      @CookieParam("cookie1")
      @DefaultValue("cookie1DefaultValue")
      private Cookie cookie1;

      @CookieParam("cookie2")
      @DefaultValue("cookie2DefaultValue")
      private String cookie2;


      public String getPath() {
        return path;
      }


      public void setPath(String path) {
        this.path = path;
      }


      public String getForm() {
        return form;
      }


      public void setForm(String form) {
        this.form = form;
      }


      public String getHeader() {
        return header;
      }


      public void setHeader(String header) {
        this.header = header;
      }


      public Cookie getCookie1() {
        return cookie1;
      }


      public void setCookie1(Cookie cookie1) {
        this.cookie1 = cookie1;
      }


      public String getCookie2() {
        return cookie2;
      }


      public void setCookie2(String cookie2) {
        this.cookie2 = cookie2;
      }

    }
  }



  @Path("/{path}")
  public interface JakartaInternalsRecord extends BaseJaxrs2InternalsRecord<InputRecord, Cookie> {

    @Override
    @POST
    void beanParameters(@BeanParam InputRecord input);

    record InputRecord(
        @DefaultValue("defaultPath") @PathParam("path") String path,
        @Encoded @DefaultValue("defaultForm") @FormParam("form") String form,
        @DefaultValue("defaultHeader") @HeaderParam("X-Custom-Header") String header,
        @CookieParam("cookie1") Cookie cookie1,
        @CookieParam("cookie2") @DefaultValue("cookie2DefaultValue") String cookie2


    ) {

    }
  }

  @Override
  protected Class<? extends Methods> methodsClass() {
    return Methods.class;
  }

  @Override
  protected Class<? extends CustomMethod> customMethodClass() {
    return CustomMethod.class;
  }

  @Override
  protected Class<? extends WithQueryParamsInPath> withQueryParamsInPathClass() {
    return WithQueryParamsInPath.class;
  }

  @Override
  protected Class<? extends ProducesAndConsumes> producesAndConsumesClass() {
    return ProducesAndConsumes.class;
  }

  @Override
  protected Class<? extends BodyParams> bodyParamsClass() {
    return BodyParams.class;
  }

  @Override
  protected Class<? extends EmptyPathOnType> emptyPathOnTypeClass() {
    return EmptyPathOnType.class;
  }

  @Override
  protected Class<? extends PathOnType> pathOnTypeClass() {
    return PathOnType.class;
  }

  @Override
  protected Class<? extends ComplexPathOnType> complexPathOnTypeClass()

  {
    return ComplexPathOnType.class;
  }

  @Override
  protected Class<? extends WithURIParam> withURIParamClass() {
    return WithURIParam.class;
  }

  @Override
  protected Class<? extends WithPathAndQueryParams> withPathAndQueryParamsClass() {
    return WithPathAndQueryParams.class;
  }

  @Override
  protected Class<? extends FormParams> formParamsClass() {
    return FormParams.class;
  }

  @Override
  protected Class<? extends HeaderParams> headerParamsClass() {
    return HeaderParams.class;
  }

  @Override
  protected Class<? extends PathsWithoutAnySlashes> pathsWithoutAnySlashesClass() {
    return PathsWithoutAnySlashes.class;
  }

  @Override
  protected Class<? extends PathsWithSomeSlashes> pathsWithSomeSlashesClass() {
    return PathsWithSomeSlashes.class;
  }

  @Override
  protected Class<? extends PathsWithSomeOtherSlashes> pathsWithSomeOtherSlashesClass() {
    return PathsWithSomeOtherSlashes.class;
  }

  @Override
  protected Class<? extends ClassRootPath> classRootPathClass() {
    return ClassRootPath.class;
  }

  @Override
  protected Class<? extends ClassPathWithTrailingSlash> classPathWithTrailingSlashClass() {
    return ClassPathWithTrailingSlash.class;
  }

  @Override
  protected Class<? extends MethodWithFirstPathThenGetWithoutLeadingSlash> methodWithFirstPathThenGetWithoutLeadingSlashClass() {
    return MethodWithFirstPathThenGetWithoutLeadingSlash.class;
  }

  @Override
  protected Class<? extends MixedAnnotations> mixedAnnotationsClass() {
    return MixedAnnotations.class;
  }

  @Override
  protected Class<? extends DefaultValues> DefaultValueClass() {
    return DefaultValues.class;
  }

  @Override
  protected Class<? extends MatrixParams> MatrixParamClass() {
    return MatrixParams.class;
  }

  @Override
  protected Class<? extends EmptyCookieParam> EmptyCookieParamClass() {
    return EmptyCookieParam.class;
  }

  @Override
  protected Class<? extends BaseCookieParams<?>> CookieParamClass() {
    return CookieParams.class;
  }

  @Override
  protected Class<? extends EncodedType> EncodedTypeClass() {
    return EncodedType.class;
  }

  @Override
  protected Class<? extends EncodedMethodAndParam> EncodedClass() {
    return EncodedMethodAndParam.class;
  }

  @Override
  protected Class<? extends BaseJaxrs2Internals<?, ?>> BeanClass() {
    return JakartaInternals.class;
  }

  @Override
  protected Class<? extends BaseJaxrs2InternalsRecord<?, ?>> RecordClass() {
    return JakartaInternalsRecord.class;
  }


}
