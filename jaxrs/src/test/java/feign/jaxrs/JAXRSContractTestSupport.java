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
import static feign.assertj.MockWebServerAssertions.assertThat;
import static java.util.Arrays.asList;
import static org.assertj.core.data.MapEntry.entry;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.core.Cookie;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import feign.DeclarativeContract;
import feign.Feign;
import feign.MethodMetadata;
import feign.codec.Decoder;
import feign.jaxrs.JAXRSContractTest.BaseCookieParams;
import feign.jaxrs.JAXRSContractTest.BodyParams;
import feign.jaxrs.JAXRSContractTest.ClassPathWithTrailingSlash;
import feign.jaxrs.JAXRSContractTest.ClassRootPath;
import feign.jaxrs.JAXRSContractTest.ComplexPathOnType;
import feign.jaxrs.JAXRSContractTest.CookieParams;
import feign.jaxrs.JAXRSContractTest.CustomMethod;
import feign.jaxrs.JAXRSContractTest.DefaultValues;
import feign.jaxrs.JAXRSContractTest.EmptyCookieParam;
import feign.jaxrs.JAXRSContractTest.EmptyPathOnType;
import feign.jaxrs.JAXRSContractTest.EncodedMethodAndParam;
import feign.jaxrs.JAXRSContractTest.EncodedType;
import feign.jaxrs.JAXRSContractTest.FormParams;
import feign.jaxrs.JAXRSContractTest.HeaderParams;
import feign.jaxrs.JAXRSContractTest.MatrixParams;
import feign.jaxrs.JAXRSContractTest.MethodWithFirstPathThenGetWithoutLeadingSlash;
import feign.jaxrs.JAXRSContractTest.Methods;
import feign.jaxrs.JAXRSContractTest.MixedAnnotations;
import feign.jaxrs.JAXRSContractTest.PathOnType;
import feign.jaxrs.JAXRSContractTest.PathsWithSomeOtherSlashes;
import feign.jaxrs.JAXRSContractTest.PathsWithSomeSlashes;
import feign.jaxrs.JAXRSContractTest.PathsWithoutAnySlashes;
import feign.jaxrs.JAXRSContractTest.ProducesAndConsumes;
import feign.jaxrs.JAXRSContractTest.WithPathAndQueryParams;
import feign.jaxrs.JAXRSContractTest.WithQueryParamsInPath;
import feign.jaxrs.JAXRSContractTest.WithURIParam;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

public abstract class JAXRSContractTestSupport<E extends DeclarativeContract> {

	@SuppressWarnings("unused")
	private static final List<String> STRING_LIST = null;

	protected abstract MethodMetadata parseAndValidateMetadata(Class<?> targetType, String method,
			Class<?>... parameterTypes) throws NoSuchMethodException;

	protected abstract E createContract();

	@SuppressWarnings("deprecation")
	@Rule
	public final ExpectedException thrown = ExpectedException.none();

	protected E contract = createContract();

	@Rule
	public final MockWebServer server = new MockWebServer();

	public final class JAXRSTestBuilder {
		private final Feign.Builder delegate = new Feign.Builder().contract(contract).decoder(new Decoder.Default())
				.encoder(new UrlEncoder()).requestInterceptor(new JAXRSRequestInterceptor())
//				.logger(new Logger.JavaLogger("feign-jaxrs").appendToFile("feign-jaxrs.log")).logLevel(feign.Logger.Level.FULL)
				;
		public <T> T target(String url, Class<T> clazz) {
			return delegate.target(clazz, url);
		}
	}

	public static Object newCookie(String value, Class<?> clazz) throws NoSuchMethodException, SecurityException,
			IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		Method method = clazz.getMethod("valueOf", String.class);
		return method.invoke(null, value);
	}

	@Test
	public void httpMethods() throws Exception {
		assertThat(parseAndValidateMetadata(methodsClass(), "post").template()).hasMethod("POST");

		assertThat(parseAndValidateMetadata(methodsClass(), "put").template()).hasMethod("PUT");

		assertThat(parseAndValidateMetadata(methodsClass(), "get").template()).hasMethod("GET");

		assertThat(parseAndValidateMetadata(methodsClass(), "delete").template()).hasMethod("DELETE");
	}

	@Test
	public void customMethodWithoutPath() throws Exception {
		assertThat(parseAndValidateMetadata(customMethodClass(), "patch").template()).hasMethod("PATCH").hasUrl("/");
	}

	@Test
	public void queryParamsInPathExtract() throws Exception {
		assertThat(parseAndValidateMetadata(withQueryParamsInPathClass(), "none").template()).hasPath("/").hasQueries();

		assertThat(parseAndValidateMetadata(withQueryParamsInPathClass(), "one").template()).hasPath("/")
				.hasQueries(entry("Action", asList("GetUser")));

		assertThat(parseAndValidateMetadata(withQueryParamsInPathClass(), "two").template()).hasPath("/")
				.hasQueries(entry("Action", asList("GetUser")), entry("Version", asList("2010-05-08")));

		assertThat(parseAndValidateMetadata(withQueryParamsInPathClass(), "three").template()).hasPath("/").hasQueries(
				entry("Action", asList("GetUser")), entry("Version", asList("2010-05-08")),
				entry("limit", asList("1")));

		assertThat(parseAndValidateMetadata(withQueryParamsInPathClass(), "empty").template()).hasPath("/").hasQueries(
				entry("flag", new ArrayList<>()), entry("Action", asList("GetUser")),
				entry("Version", asList("2010-05-08")));
	}

	@Test
	public void producesAddsAcceptHeader() throws Exception {
		final MethodMetadata md = parseAndValidateMetadata(producesAndConsumesClass(), "produces");

		/* multiple @Produces annotations should be additive */
		assertThat(md.template()).hasHeaders(entry("Content-Type", asList("application/json")),
				entry("Accept", asList("application/xml")));
	}

	@Test
	public void producesMultipleAddsAcceptHeader() throws Exception {
		final MethodMetadata md = parseAndValidateMetadata(producesAndConsumesClass(), "producesMultiple");

		assertThat(md.template()).hasHeaders(entry("Content-Type", Collections.singletonList("application/json")),
				entry("Accept", asList("application/xml", "text/plain")));
	}

	@Test
	public void producesNada() throws Exception {
		thrown.expect(IllegalStateException.class);
		thrown.expectMessage("Produces.value() was empty on ProducesAndConsumes#producesNada");

		parseAndValidateMetadata(producesAndConsumesClass(), "producesNada");
	}

	@Test
	public void producesEmpty() throws Exception {

		thrown.expect(IllegalStateException.class);
		thrown.expectMessage("Produces.value() was empty on ProducesAndConsumes#producesEmpty");

		parseAndValidateMetadata(producesAndConsumesClass(), "producesEmpty");
	}

	@Test
	public void consumesAddsContentTypeHeader() throws Exception {
		final MethodMetadata md = parseAndValidateMetadata(producesAndConsumesClass(), "consumes");

		/* multiple @Consumes annotations are additive */
		assertThat(md.template()).hasHeaders(entry("Content-Type", asList("application/xml")),
				entry("Accept", asList("text/html")));
	}

	@Test
	public void consumesMultipleAddsContentTypeHeader() throws Exception {
		final MethodMetadata md = parseAndValidateMetadata(producesAndConsumesClass(), "consumesMultiple");

		assertThat(md.template()).hasHeaders(entry("Content-Type", asList("application/xml")),
				entry("Accept", Collections.singletonList("text/html")));
	}

	@Test
	public void consumesNada() throws Exception {
		thrown.expect(IllegalStateException.class);
		thrown.expectMessage("Consumes.value() was empty on ProducesAndConsumes#consumesNada");

		parseAndValidateMetadata(producesAndConsumesClass(), "consumesNada");
	}

	@Test
	public void consumesEmpty() throws Exception {
		thrown.expect(IllegalStateException.class);
		thrown.expectMessage("Consumes.value() was empty on ProducesAndConsumes#consumesEmpty");

		parseAndValidateMetadata(producesAndConsumesClass(), "consumesEmpty");
	}

	@Test
	public void producesAndConsumesOnClassAddsHeader() throws Exception {
		final MethodMetadata md = parseAndValidateMetadata(producesAndConsumesClass(), "producesAndConsumes");

		assertThat(md.template()).hasHeaders(entry("Content-Type", asList("application/json")),
				entry("Accept", asList("text/html")));
	}

	@Test
	public void bodyParamIsGeneric() throws Exception {
		final MethodMetadata md = parseAndValidateMetadata(bodyParamsClass(), "post", List.class);

		assertThat(md.bodyIndex()).isNull();
		assertThat(md.bodyType()).isNull();
	}

	@Test
	public void tooManyBodies() throws Exception {
		parseAndValidateMetadata(bodyParamsClass(), "tooMany", List.class, List.class);
	}

	@Test
	public void emptyPathOnType() throws Exception {
		assertThat(parseAndValidateMetadata(emptyPathOnTypeClass(), "base").template()).hasUrl("/");
	}

	@Test
	public void emptyPathOnTypeSpecific() throws Exception {
		assertThat(parseAndValidateMetadata(emptyPathOnTypeClass(), "get").template()).hasUrl("/specific");
	}

	@Test
	public void parsePathMethod() throws Exception {
		assertThat(parseAndValidateMetadata(pathOnTypeClass(), "base").template()).hasUrl("/base");

		assertThat(parseAndValidateMetadata(pathOnTypeClass(), "get").template()).hasUrl("/base/specific");
	}

	@Test
	public void emptyPathOnMethod() throws Exception {
		assertThat(parseAndValidateMetadata(pathOnTypeClass(), "emptyPath").template()).hasUrl("/base");
	}

	@Test
	public void emptyPathParam() throws Exception {
		thrown.expect(IllegalStateException.class);
		thrown.expectMessage("PathParam.value() was empty on parameter 0");

		parseAndValidateMetadata(pathOnTypeClass(), "emptyPathParam", String.class);
	}

	@Test
	public void pathParamWithSpaces() throws Exception {
		assertThat(parseAndValidateMetadata(pathOnTypeClass(), "pathParamWithSpaces", String.class).template())
				.hasUrl("/base/{param}");
	}

	@Test
	public void regexPathOnMethodOrType() throws Exception {
		assertThat(parseAndValidateMetadata(pathOnTypeClass(), "pathParamWithRegex", String.class).template())
				.hasUrl("/base/regex/{param}");

		assertThat(parseAndValidateMetadata(pathOnTypeClass(), "pathParamWithMultipleRegex", String.class, String.class)
				.template()).hasUrl("/base/regex/{param1}/{param2}");

		assertThat(parseAndValidateMetadata(complexPathOnTypeClass(), "pathParamWithMultipleRegex", String.class,
				String.class).template()).hasUrl("/{baseparam}/regex/{param1}/{param2}");
	}

	@Test
	public void withPathAndURIParams() throws Exception {
		final MethodMetadata md = parseAndValidateMetadata(withURIParamClass(), "uriParam", String.class, URI.class,
				String.class);

		assertThat(md.indexToName()).containsExactly(entry(0, asList("1")),
				// Skips 1 as it is a url index!
				entry(2, asList("2")));

		assertThat(md.urlIndex()).isEqualTo(1);
	}

	@Test
	public void pathAndQueryParams() throws Exception {
		final MethodMetadata md = parseAndValidateMetadata(withPathAndQueryParamsClass(), "recordsByNameAndType",
				int.class, String.class, String.class);

		assertThat(md.template()).hasQueries(entry("name", asList("{name}")), entry("type", asList("{type}")));

		assertThat(md.indexToName()).containsExactly(entry(0, asList("domainId")), entry(1, asList("name")),
				entry(2, asList("type")));
	}

	@Test
	public void emptyQueryParam() throws Exception {
		thrown.expect(IllegalStateException.class);
		thrown.expectMessage("QueryParam.value() was empty on parameter 0");
		parseAndValidateMetadata(withPathAndQueryParamsClass(), "empty", String.class);
	}

	@Test
	public void formParamsParseIntoIndexToName() throws Exception {
		final MethodMetadata md = parseAndValidateMetadata(formParamsClass(), "login", String.class, String.class,
				String.class);

		assertThat(md.formParams()).containsExactly("customer_name", "user_name", "password");

		assertThat(md.indexToName()).containsExactly(entry(0, asList("customer_name")), entry(1, asList("user_name")),
				entry(2, asList("password")));
	}

	/** Body type is only for the body param. */
	@Test
	public void formParamsDoesNotSetBodyType() throws Exception {
		final MethodMetadata md = parseAndValidateMetadata(formParamsClass(), "login", String.class, String.class,
				String.class);

		assertThat(md.bodyType()).isNull();
	}

	@Test
	public void emptyFormParam() throws Exception {
		thrown.expect(IllegalStateException.class);
		thrown.expectMessage("FormParam.value() was empty on parameter 0");

		parseAndValidateMetadata(formParamsClass(), "emptyFormParam", String.class);
	}

	@Test
	public void headerParamsParseIntoIndexToName() throws Exception {
		final MethodMetadata md = parseAndValidateMetadata(headerParamsClass(), "logout", String.class);

		assertThat(md.template()).hasHeaders(entry("Auth-Token", asList("{Auth-Token}")));

		assertThat(md.indexToName()).containsExactly(entry(0, asList("Auth-Token")));
	}

	@Test
	public void emptyHeaderParam() throws Exception {
		thrown.expect(IllegalStateException.class);
		thrown.expectMessage("HeaderParam.value() was empty on parameter 0");
		parseAndValidateMetadata(headerParamsClass(), "emptyHeaderParam", String.class);
	}

	@Test
	public void pathsWithoutSlashesParseCorrectly() throws Exception {
		assertThat(parseAndValidateMetadata(pathsWithoutAnySlashesClass(), "get").template()).hasUrl("/base/specific");
	}

	@Test
	public void pathsWithSomeSlashesParseCorrectly() throws Exception {
		assertThat(parseAndValidateMetadata(pathsWithSomeSlashesClass(), "get").template()).hasUrl("/base/specific");
	}

	@Test
	public void pathsWithSomeOtherSlashesParseCorrectly() throws Exception {
		assertThat(parseAndValidateMetadata(pathsWithSomeOtherSlashesClass(), "get").template())
				.hasUrl("/base/specific");
	}

	@Test
	public void classWithRootPathParsesCorrectly() throws Exception {
		assertThat(parseAndValidateMetadata(classRootPathClass(), "get").template()).hasUrl("/specific");
	}

	@Test
	public void classPathWithTrailingSlashParsesCorrectly() throws Exception {
		assertThat(parseAndValidateMetadata(classPathWithTrailingSlashClass(), "get").template())
				.hasUrl("/base/specific");
	}

	@Test
	public void methodPathWithoutLeadingSlashParsesCorrectly() throws Exception {
		assertThat(parseAndValidateMetadata(methodWithFirstPathThenGetWithoutLeadingSlashClass(), "get").template())
				.hasUrl("/base/specific");
	}

	@Test
	public void producesWithHeaderParamContainAllHeaders() throws Exception {
		assertThat(parseAndValidateMetadata(mixedAnnotationsClass(), "getWithHeaders", String.class, String.class,
				String.class).template()).hasHeaders(entry("Accept", Arrays.asList("application/json", "{Accept}")))
						.hasQueries(entry("multiple", Arrays.asList("stuff", "{multiple}")),
								entry("another", Collections.singletonList("{another}")));
	}

	@Test
	public void PathParamWithDefaultValue() throws Exception {

		server.enqueue(new MockResponse());
		DefaultValues api = new JAXRSTestBuilder().target("http://localhost:" + server.getPort(), DefaultValueClass());
		api.path(null, URI.create("http://localhost:" + server.getPort()), null);
		assertThat(server.takeRequest()).hasPath("/defaultPath1/defaultPath2");
	}

	@Test
	public void queryParamWithDefauleValue() throws Exception {

		server.enqueue(new MockResponse());
		DefaultValues api = new JAXRSTestBuilder().target("http://localhost:" + server.getPort(), DefaultValueClass());
		api.query(1, null, null);
		assertThat(server.takeRequest()).hasPath("/domains/1/records?name=defaultName&type=defaultType");
	}

	@Test
	public void formParamWithDefaultValue() throws Exception {

		server.enqueue(new MockResponse());
		DefaultValues api = new JAXRSTestBuilder().target("http://localhost:" + server.getPort(), DefaultValueClass());
		api.form(null, null, null);
		assertThat(server.takeRequest()).hasBody("customer_name=netflix&user_name=denominator&password=password");
	}

	@Test
	public void headerParamWithDefaultValue() throws Exception {
		assertThat(parseAndValidateMetadata(DefaultValueClass(), "header", String.class).template())
				.hasHeaders(entry("Auth-Token", asList("{Auth-Token}")));

		server.enqueue(new MockResponse());
		DefaultValues api = new JAXRSTestBuilder().target("http://localhost:" + server.getPort(), DefaultValueClass());
		api.header(null);
		assertThat(server.takeRequest()).hasHeaders(entry("Auth-Token", Arrays.asList("defaultToken")));
	}

	@Test
	public void cookieParamWithDefaultValue() throws Exception {
		server.enqueue(new MockResponse());
		DefaultValues api = new JAXRSTestBuilder().target("http://localhost:" + server.getPort(), DefaultValueClass());
		api.cookie(null, null);
		assertThat(server.takeRequest())
				.hasHeaders(entry("Cookie", Arrays.asList("cookie1=defaultCookie1Value, cookie2=defaultCookie2Value")));
	}

	@Test
	public void matrixParamWithDefaultValue() throws Exception {
		server.enqueue(new MockResponse());
		MatrixParams api = new JAXRSTestBuilder().target("http://localhost:" + server.getPort(), MatrixParamClass());
		api.multiPath("person", null, "location", null, 1);
		assertThat(server.takeRequest()).hasPath("/res/person;name=tom/location;country=US/?page=1");

	}

	@Test
	public void withSinglePathAndMatrixParam() throws Exception {
		server.enqueue(new MockResponse());
		MatrixParams api = new JAXRSTestBuilder().target("http://localhost:" + server.getPort(), MatrixParamClass());
		api.singlePath("1970", "tom", "US", "economics");
		assertThat(server.takeRequest()).hasPath("/1970;author=tom;country=US/?category=economics");
	}

	@Test
	public void withMultiPathAndMatrixParam() throws Exception {
		server.enqueue(new MockResponse());
		MatrixParams api = new JAXRSTestBuilder().target("http://localhost:" + server.getPort(), MatrixParamClass());
		api.multiPath("person", "tom", "location", "UK", 1);
		assertThat(server.takeRequest()).hasPath("/res/person;name=tom/location;country=UK/?page=1");
	}

	@Test
	public void cookieParams() throws Exception {
		server.enqueue(new MockResponse());
		BaseCookieParams<?> api = new JAXRSTestBuilder().target("http://localhost:" + server.getPort(),
				CookieParamClass());
		api.withString("cookie1Value", "cookie2Value");
		assertThat(server.takeRequest())
				.hasHeaders(entry("Cookie", Arrays.asList("cookie1=cookie1Value, cookie2=cookie2Value")));

	}

	@Test
	public void emptyCookieParam() throws Exception {
		thrown.expect(IllegalStateException.class);
		thrown.expectMessage("CookieParam.value() was empty on parameter 0");
		parseAndValidateMetadata(EmptyCookieParamClass(), "empty", String.class);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void cookieParamWithCookieType() throws Exception {

		server.enqueue(new MockResponse());
		BaseCookieParams<Object> api = (BaseCookieParams<Object>) new JAXRSTestBuilder()
				.target("http://localhost:" + server.getPort(), CookieParamClass());
		Object cookie1 = api.valueOf("cookie1=cookie1Value");
		Object cookie2 = cookie1;

		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("The Cookie's name 'cookie1' do not match with CookieParam's value 'cookie2'!");
		api.withType(cookie1, cookie2);

		cookie2 = new Cookie("cookie2", "cookie2Value");
		api.withType(cookie1, cookie2);
		assertThat(server.takeRequest())
				.hasHeaders(entry("Cookie", Arrays.asList("cookie1=cookie1Value, cookie2=cookie2Value")));
	}

	@Test
	public void encodedType() throws Exception {
		server.enqueue(new MockResponse());
		EncodedType api = new JAXRSTestBuilder().target("http://localhost:" + server.getPort(), EncodedTypeClass());
		String value = ":";
		api.get(value);
		assertThat(server.takeRequest()).hasPath("/" + value);

		server.enqueue(new MockResponse());
		api.post(value);
		assertThat(server.takeRequest()).hasPath("/" + value);
	}

	@Test
	public void encodedMethod() throws Exception {
		server.enqueue(new MockResponse());
		EncodedMethodAndParam api = new JAXRSTestBuilder().target("http://localhost:" + server.getPort(),
				EncodedClass());
		String path = ":";
		String encodedQueryParam = ":";
		api.method(path, encodedQueryParam);
		assertThat(server.takeRequest()).hasPath("/" + path + "?encodedParam=" + encodedQueryParam);

		server.enqueue(new MockResponse());
		api.methodNotAnnotated(path, encodedQueryParam);
		assertThat(server.takeRequest()).hasPath("/" + URLEncoder.encode(path, Charset.defaultCharset())
				+ "?encodedParam=" + URLEncoder.encode(encodedQueryParam, Charset.defaultCharset())

		);

	}

	@Test
	public void encodedPathParam() throws Exception {
		server.enqueue(new MockResponse());
		EncodedMethodAndParam api = new JAXRSTestBuilder().target("http://localhost:" + server.getPort(),
				EncodedClass());
		String value = ":";
		api.pathParam(value);
		assertThat(server.takeRequest()).hasPath("/" + value);

		server.enqueue(new MockResponse());
		api.pathParamNotAnnotated(value);
		assertThat(server.takeRequest()).hasPath("/" + URLEncoder.encode(value, Charset.defaultCharset()));
	}

	@Test
	public void encodedQueryParam() throws Exception {
		server.enqueue(new MockResponse());
		EncodedMethodAndParam api = new JAXRSTestBuilder().target("http://localhost:" + server.getPort(),
				EncodedClass());
		String value = ":";
		api.queryParam(value);
		assertThat(server.takeRequest()).hasPath("/res?encodedParam=" + value);

		server.enqueue(new MockResponse());
		api.queryParamNotAnnotated(value);
		assertThat(server.takeRequest())
				.hasPath("/res?encodedParam=" + URLEncoder.encode(value, Charset.defaultCharset()));
	}

	@Test
	public void encodedFormParam() throws Exception {
		server.enqueue(new MockResponse());
		EncodedMethodAndParam api = new JAXRSTestBuilder().target("http://localhost:" + server.getPort(),
				EncodedClass());
		String value = ":";
		api.formParam(value);
		assertThat(server.takeRequest()).hasBody("encodedParam=" + value);

		server.enqueue(new MockResponse());
		api.formParamNotAnnotated(value);
		assertThat(server.takeRequest()).hasBody("encodedParam=" + URLEncoder.encode(value, Charset.defaultCharset()));
	}

	@Test
	public void encodedMatrixParam() throws Exception {
		server.enqueue(new MockResponse());
		EncodedMethodAndParam api = new JAXRSTestBuilder().target("http://localhost:" + server.getPort(),
				EncodedClass());
		String value1 = ":";
		String value2 = ":";
		api.matrixParam(value1, value2, "economics");
		assertThat(server.takeRequest())
				.hasPath("/res;author=" + value1 + ";country=" + value2 + "/?category=economics");

		server.enqueue(new MockResponse());
		api.matrixParamNotAnnotated(value1, value2, "economics");
		assertThat(server.takeRequest()).hasPath("/res;author=" + URLEncoder.encode(value1, Charset.defaultCharset())
				+ ";country=" + URLEncoder.encode(value2, Charset.defaultCharset()) + "/?category=economics");

	}

	protected Class<? extends Methods> methodsClass() {
		return Methods.class;
	}

	protected Class<? extends CustomMethod> customMethodClass() {
		return CustomMethod.class;
	}

	protected Class<? extends WithQueryParamsInPath> withQueryParamsInPathClass() {
		return WithQueryParamsInPath.class;
	}

	protected Class<? extends ProducesAndConsumes> producesAndConsumesClass() {
		return ProducesAndConsumes.class;
	}

	protected Class<? extends BodyParams> bodyParamsClass() {
		return BodyParams.class;
	}

	protected Class<? extends EmptyPathOnType> emptyPathOnTypeClass() {
		return EmptyPathOnType.class;
	}

	protected Class<? extends PathOnType> pathOnTypeClass() {
		return PathOnType.class;
	}

	protected Class<? extends ComplexPathOnType> complexPathOnTypeClass()

	{
		return ComplexPathOnType.class;
	}

	protected Class<? extends WithURIParam> withURIParamClass() {
		return WithURIParam.class;
	}

	protected Class<? extends WithPathAndQueryParams> withPathAndQueryParamsClass() {
		return WithPathAndQueryParams.class;
	}

	protected Class<? extends FormParams> formParamsClass() {
		return FormParams.class;
	}

	protected Class<? extends HeaderParams> headerParamsClass() {
		return HeaderParams.class;
	}

	protected Class<? extends PathsWithoutAnySlashes> pathsWithoutAnySlashesClass() {
		return PathsWithoutAnySlashes.class;
	}

	protected Class<? extends PathsWithSomeSlashes> pathsWithSomeSlashesClass() {
		return PathsWithSomeSlashes.class;
	}

	protected Class<? extends PathsWithSomeOtherSlashes> pathsWithSomeOtherSlashesClass() {
		return PathsWithSomeOtherSlashes.class;
	}

	protected Class<? extends ClassRootPath> classRootPathClass() {
		return ClassRootPath.class;
	}

	protected Class<? extends ClassPathWithTrailingSlash> classPathWithTrailingSlashClass() {
		return ClassPathWithTrailingSlash.class;
	}

	protected Class<? extends MethodWithFirstPathThenGetWithoutLeadingSlash> methodWithFirstPathThenGetWithoutLeadingSlashClass() {
		return MethodWithFirstPathThenGetWithoutLeadingSlash.class;
	}

	protected Class<? extends MixedAnnotations> mixedAnnotationsClass() {
		return MixedAnnotations.class;
	}

	protected Class<? extends DefaultValues> DefaultValueClass() {
		return DefaultValues.class;
	}

	protected Class<? extends MatrixParams> MatrixParamClass() {
		return MatrixParams.class;
	}

	protected Class<? extends EmptyCookieParam> EmptyCookieParamClass() {
		return EmptyCookieParam.class;
	}

	protected Class<? extends BaseCookieParams<?>> CookieParamClass() {
		return CookieParams.class;
	}

	protected Class<? extends EncodedType> EncodedTypeClass() {
		return EncodedType.class;
	}

	protected Class<? extends EncodedMethodAndParam> EncodedClass() {
		return EncodedMethodAndParam.class;
	}
}
