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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.net.URI;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.CookieParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.Encoded;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.MatrixParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;

import feign.MethodMetadata;
import feign.Response;

/**
 * Tests interfaces defined per {@link JAXRSContract} are interpreted into
 * expected {@link feign .RequestTemplate template} instances.
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
		Response pathParamWithMultipleRegex(@PathParam("param1") String param1, @PathParam("param2") String param2);
	}

	@Path("/{baseparam: [0-9]+}")
	protected interface ComplexPathOnType {

		@GET
		@Path("regex/{param1:[0-9]*}/{  param2 : .+}")
		Response pathParamWithMultipleRegex(@PathParam("param1") String param1, @PathParam("param2") String param2);
	}

	protected interface WithURIParam {

		@GET
		@Path("/{1}/{2}")
		Response uriParam(@PathParam("1") String one, URI endpoint, @PathParam("2") String two);
	}

	protected interface WithPathAndQueryParams {

		@GET
		@Path("/domains/{domainId}/records")
		Response recordsByNameAndType(@PathParam("domainId") int id, @QueryParam("name") String nameFilter,
				@QueryParam("type") String typeFilter);

		@GET
		Response empty(@QueryParam("") String empty);
	}

	protected interface FormParams {

		@POST
		void login(@FormParam("customer_name") String customer, @FormParam("user_name") String user,
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
		Response getWithHeaders(@HeaderParam("Accept") String accept, @QueryParam("multiple") String multiple,
				@QueryParam("another") String another);
	}

	protected interface DefaultValues {
		@GET
		@Path("/{1}/{2}")
		Response path(@PathParam("1") @DefaultValue("defaultPath1") String one, URI endpoint,
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

	protected interface MatrixParams {

		@GET
		@Path("/{year};author={author};country={country}/")
		Response singlePath(@PathParam("year") String year,
				@MatrixParam("author") @DefaultValue("defaultAuthor") String author,
				@MatrixParam("country") @DefaultValue("defaultCountry") String country,
				@QueryParam("category") String category

		);

		@GET
		@Path("/res/{path1};name={name}/{path2};country={country}/")
		Response multiPath(@PathParam("path1") String person, @MatrixParam("name") @DefaultValue("tom") String name,
				@PathParam("path2") String location, @MatrixParam("country") @DefaultValue("US") String country,
				@QueryParam("page") int page);
	}

	protected interface EmptyCookieParam {
		@GET
		Response empty(@CookieParam("") String cookie);
	}

	public interface BaseCookieParams<T> {
		@Path("/res")
		@POST
		void withString(@CookieParam("cookie1") String cookie1, @CookieParam("cookie2") String cookie2);

		@SuppressWarnings("unchecked")
		default T valueOf(String value) throws NoSuchMethodException, SecurityException, IllegalAccessException,
				IllegalArgumentException, InvocationTargetException {
			Class<?> clazz = (Class<?>) ParameterizedType.class
					.cast(((Class<?>) getClass().getGenericInterfaces()[0]).getGenericInterfaces()[0])
					.getActualTypeArguments()[0];
			T cookie = (T) JAXRSContractTestSupport.newCookie(value, clazz);
			return cookie;

		}

		@Path("/res")
		@GET
		Response withType(@CookieParam("cookie1") T cookie, @CookieParam("cookie2") T cookie2);

	}

	protected interface CookieParams extends BaseCookieParams<Cookie> {

	}

	@Encoded
	protected interface EncodedType {
		@GET
		@Path("/{path}")
		Response get(@PathParam("path") String path);

		@POST
		@Path("/{path}")
		Response post(@PathParam("path") String path);
	}

	protected interface EncodedMethodAndParam {
		@GET
		@Path("/{path}")
		@Encoded
		Response method(@PathParam("path") String path, @QueryParam("encodedParam") String encodedParam);

		@GET
		@Path("/{path}")
		Response methodNotAnnotated(@PathParam("path") String path, @QueryParam("encodedParam") String encodedParam);

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
				@Encoded @MatrixParam("country") String country, @QueryParam("category") String category);

		@GET
		@Path("/res;author={author};country={country}/")
		Response matrixParamNotAnnotated(@MatrixParam("author") String author, @MatrixParam("country") String country,
				@QueryParam("category") String category);

	}

	@Override
	protected JAXRSContract createContract() {
		return new JAXRSContract();
	}

	@Override
	protected MethodMetadata parseAndValidateMetadata(Class<?> targetType, String method, Class<?>... parameterTypes)
			throws NoSuchMethodException {
		return contract.parseAndValidateMetadata(targetType, targetType.getMethod(method, parameterTypes));
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
	protected Class<? extends ComplexPathOnType> complexPathOnTypeClass() {
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
	protected Class<? extends BaseCookieParams<?>> CookieParamClass() {
		return CookieParams.class;
	}

	@Override
	protected Class<? extends EmptyCookieParam> EmptyCookieParamClass() {
		return EmptyCookieParam.class;
	}

	@Override
	protected Class<? extends MatrixParams> MatrixParamClass() {
		return MatrixParams.class;
	}

	@Override
	protected Class<? extends EncodedType> EncodedTypeClass() {
		return EncodedType.class;
	}

	@Override
	protected Class<? extends EncodedMethodAndParam> EncodedClass() {
		return EncodedMethodAndParam.class;

	}

}
