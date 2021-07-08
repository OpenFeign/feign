/**
 * Copyright 2017-2019 The Feign Authors
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
package feign.error;

import feign.Request;
import feign.codec.Decoder;
import feign.optionals.OptionalDecoder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.Parameter;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static feign.error.AnnotationErrorDecoderExceptionConstructorsTest.TestClientInterfaceWithDifferentExceptionConstructors;
import static feign.error.AnnotationErrorDecoderExceptionConstructorsTest.TestClientInterfaceWithDifferentExceptionConstructors.*;

@RunWith(Parameterized.class)
public class AnnotationErrorDecoderExceptionConstructorsTest extends
    AbstractAnnotationErrorDecoderTest<TestClientInterfaceWithDifferentExceptionConstructors> {


  private static final String NO_BODY = "NO BODY";
  private static final Object NULL_BODY = null;
  private static final String NON_NULL_BODY = "A GIVEN BODY";
  private static final feign.Request REQUEST = feign.Request.create(feign.Request.HttpMethod.GET,
      "http://test", Collections.emptyMap(), Request.Body.empty(), null);
  private static final feign.Request NO_REQUEST = null;
  private static final Map<String, Collection<String>> NON_NULL_HEADERS =
      new HashMap<String, Collection<String>>();
  private static final Map<String, Collection<String>> NO_HEADERS = null;


  @Override
  public Class<TestClientInterfaceWithDifferentExceptionConstructors> interfaceAtTest() {
    return TestClientInterfaceWithDifferentExceptionConstructors.class;
  }

  @Parameters(
      name = "{0}: When error code ({1}) on method ({2}) should return exception type ({3})")
  public static Iterable<Object[]> data() {
    return Arrays.asList(new Object[][] {
        {"Test Default Constructor", 500, DefaultConstructorException.class, NO_REQUEST, NO_BODY,
            NO_HEADERS},
        {"test Default Constructor", 501, DeclaredDefaultConstructorException.class, NO_REQUEST,
            NO_BODY,
            NO_HEADERS},
        {"test Default Constructor", 502,
            DeclaredDefaultConstructorWithOtherConstructorsException.class, NO_REQUEST, NO_BODY,
            NO_HEADERS},
        {"test Declared Constructor", 503, DefinedConstructorWithNoAnnotationForBody.class,
            NO_REQUEST,
            NON_NULL_BODY, NO_HEADERS},
        {"test Declared Constructor", 504, DefinedConstructorWithAnnotationForBody.class,
            NO_REQUEST, NON_NULL_BODY, NO_HEADERS},
        {"test Declared Constructor", 505, DefinedConstructorWithAnnotationForBodyAndHeaders.class,
            NO_REQUEST, NON_NULL_BODY, NON_NULL_HEADERS},
        {"test Declared Constructor", 506,
            DefinedConstructorWithAnnotationForBodyAndHeadersSecondOrder.class, NO_REQUEST,
            NON_NULL_BODY,
            NON_NULL_HEADERS},
        {"test Declared Constructor", 507, DefinedConstructorWithAnnotationForHeaders.class,
            NO_REQUEST, NO_BODY, NON_NULL_HEADERS},
        {"test Declared Constructor", 508,
            DefinedConstructorWithAnnotationForHeadersButNotForBody.class, NO_REQUEST,
            NON_NULL_BODY,
            NON_NULL_HEADERS},
        {"test Declared Constructor", 509,
            DefinedConstructorWithAnnotationForNonSupportedBody.class, NO_REQUEST, NULL_BODY,
            NO_HEADERS},
        {"test Declared Constructor", 510,
            DefinedConstructorWithAnnotationForOptionalBody.class, NO_REQUEST,
            Optional.of(NON_NULL_BODY),
            NO_HEADERS},
        {"test Declared Constructor", 511,
            DefinedConstructorWithRequest.class, REQUEST, NO_BODY,
            NO_HEADERS},
        {"test Declared Constructor", 512,
            DefinedConstructorWithRequestAndResponseBody.class, REQUEST, NON_NULL_BODY,
            NO_HEADERS},
        {"test Declared Constructor", 513,
            DefinedConstructorWithRequestAndAnnotationForResponseBody.class, REQUEST, NON_NULL_BODY,
            NO_HEADERS},
        {"test Declared Constructor", 514,
            DefinedConstructorWithRequestAndResponseHeadersAndResponseBody.class, REQUEST,
            NON_NULL_BODY,
            NON_NULL_HEADERS},
        {"test Declared Constructor", 515,
            DefinedConstructorWithRequestAndResponseHeadersAndOptionalResponseBody.class, REQUEST,
            Optional.of(NON_NULL_BODY),
            NON_NULL_HEADERS}
    });
  }

  @Parameter // first data value (0) is default
  public String testName;

  @Parameter(1)
  public int errorCode;

  @Parameter(2)
  public Class<? extends Exception> expectedExceptionClass;

  @Parameter(3)
  public Object expectedRequest;

  @Parameter(4)
  public Object expectedBody;

  @Parameter(5)
  public Map<String, Collection<String>> expectedHeaders;

  @Test
  public void test() throws Exception {
    AnnotationErrorDecoder decoder = AnnotationErrorDecoder
        .builderFor(TestClientInterfaceWithDifferentExceptionConstructors.class)
        .withResponseBodyDecoder(new OptionalDecoder(new Decoder.Default()))
        .build();

    Exception genericException = decoder.decode(feignConfigKey("method1Test"),
        testResponse(errorCode, NON_NULL_BODY, NON_NULL_HEADERS));

    assertThat(genericException).isInstanceOf(expectedExceptionClass);

    ParametersException exception = (ParametersException) genericException;
    assertThat(exception.body()).isEqualTo(expectedBody);
    assertThat(exception.headers()).isEqualTo(expectedHeaders);
  }

  @Test
  public void testIfExceptionIsNotInTheList() throws Exception {
    AnnotationErrorDecoder decoder = AnnotationErrorDecoder
        .builderFor(TestClientInterfaceWithDifferentExceptionConstructors.class)
        .withResponseBodyDecoder(new OptionalDecoder(new Decoder.Default()))
        .build();

    Exception genericException = decoder.decode(feignConfigKey("method1Test"),
        testResponse(-1, NON_NULL_BODY, NON_NULL_HEADERS));

    assertThat(genericException)
        .isInstanceOf(ErrorHandling.NO_DEFAULT.class)
        .hasMessage("Endpoint responded with -1, reason: null");
  }

  interface TestClientInterfaceWithDifferentExceptionConstructors {

    @ErrorHandling(codeSpecific = {
        @ErrorCodes(codes = {500}, generate = DefaultConstructorException.class),
        @ErrorCodes(codes = {501}, generate = DeclaredDefaultConstructorException.class),
        @ErrorCodes(codes = {502},
            generate = DeclaredDefaultConstructorWithOtherConstructorsException.class),
        @ErrorCodes(codes = {503}, generate = DefinedConstructorWithNoAnnotationForBody.class),
        @ErrorCodes(codes = {504}, generate = DefinedConstructorWithAnnotationForBody.class),
        @ErrorCodes(codes = {505},
            generate = DefinedConstructorWithAnnotationForBodyAndHeaders.class),
        @ErrorCodes(codes = {506},
            generate = DefinedConstructorWithAnnotationForBodyAndHeadersSecondOrder.class),
        @ErrorCodes(codes = {507}, generate = DefinedConstructorWithAnnotationForHeaders.class),
        @ErrorCodes(codes = {508},
            generate = DefinedConstructorWithAnnotationForHeadersButNotForBody.class),
        @ErrorCodes(codes = {509},
            generate = DefinedConstructorWithAnnotationForNonSupportedBody.class),
        @ErrorCodes(codes = {510},
            generate = DefinedConstructorWithAnnotationForOptionalBody.class),
        @ErrorCodes(codes = {511},
            generate = DefinedConstructorWithRequest.class),
        @ErrorCodes(codes = {512},
            generate = DefinedConstructorWithRequestAndResponseBody.class),
        @ErrorCodes(codes = {513},
            generate = DefinedConstructorWithRequestAndAnnotationForResponseBody.class),
        @ErrorCodes(codes = {514},
            generate = DefinedConstructorWithRequestAndResponseHeadersAndResponseBody.class),
        @ErrorCodes(codes = {515},
            generate = DefinedConstructorWithRequestAndResponseHeadersAndOptionalResponseBody.class)
    })
    void method1Test();

    class ParametersException extends Exception {
      public Object body() {
        return NO_BODY;
      }

      public feign.Request request() {
        return null;
      }

      public Map<String, Collection<String>> headers() {
        return null;
      }
    }
    class DefaultConstructorException extends ParametersException {
    }

    class DeclaredDefaultConstructorException extends ParametersException {
      public DeclaredDefaultConstructorException() {}
    }

    class DeclaredDefaultConstructorWithOtherConstructorsException extends ParametersException {
      public DeclaredDefaultConstructorWithOtherConstructorsException() {}

      public DeclaredDefaultConstructorWithOtherConstructorsException(TestPojo testPojo) {
        throw new UnsupportedOperationException("Should not be called");
      }

      public DeclaredDefaultConstructorWithOtherConstructorsException(Throwable cause) {
        throw new UnsupportedOperationException("Should not be called");
      }

      public DeclaredDefaultConstructorWithOtherConstructorsException(String message,
          Throwable cause) {
        throw new UnsupportedOperationException("Should not be called");
      }
    }

    class DefinedConstructorWithNoAnnotationForBody extends ParametersException {
      String body;

      public DefinedConstructorWithNoAnnotationForBody() {
        throw new UnsupportedOperationException("Should not be called");
      }

      @FeignExceptionConstructor
      public DefinedConstructorWithNoAnnotationForBody(String body) {
        this.body = body;
      }

      public DefinedConstructorWithNoAnnotationForBody(TestPojo testPojo) {
        throw new UnsupportedOperationException("Should not be called");
      }

      @Override
      public Object body() {
        return body;
      }

    }

    class DefinedConstructorWithRequest extends ParametersException {
      feign.Request request;

      public DefinedConstructorWithRequest() {
        throw new UnsupportedOperationException("Should not be called");
      }

      @FeignExceptionConstructor
      public DefinedConstructorWithRequest(feign.Request request) {
        this.request = request;
      }

      public DefinedConstructorWithRequest(TestPojo testPojo) {
        throw new UnsupportedOperationException("Should not be called");
      }

      @Override
      public feign.Request request() {
        return request;
      }

    }

    class DefinedConstructorWithRequestAndResponseBody extends ParametersException {
      feign.Request request;
      String body;

      public DefinedConstructorWithRequestAndResponseBody() {
        throw new UnsupportedOperationException("Should not be called");
      }

      @FeignExceptionConstructor
      public DefinedConstructorWithRequestAndResponseBody(feign.Request request, String body) {
        this.request = request;
        this.body = body;
      }

      public DefinedConstructorWithRequestAndResponseBody(TestPojo testPojo) {
        throw new UnsupportedOperationException("Should not be called");
      }

      @Override
      public feign.Request request() {
        return request;
      }

      @Override
      public String body() {
        return body;
      }
    }

    class DefinedConstructorWithRequestAndAnnotationForResponseBody extends ParametersException {
      feign.Request request;
      String body;

      public DefinedConstructorWithRequestAndAnnotationForResponseBody() {
        throw new UnsupportedOperationException("Should not be called");
      }

      @FeignExceptionConstructor
      public DefinedConstructorWithRequestAndAnnotationForResponseBody(feign.Request request,
          @ResponseBody String body) {
        this.request = request;
        this.body = body;
      }

      public DefinedConstructorWithRequestAndAnnotationForResponseBody(TestPojo testPojo) {
        throw new UnsupportedOperationException("Should not be called");
      }

      @Override
      public feign.Request request() {
        return request;
      }

      @Override
      public String body() {
        return body;
      }
    }

    class DefinedConstructorWithRequestAndResponseHeadersAndResponseBody
        extends ParametersException {
      feign.Request request;
      String body;
      Map headers;

      public DefinedConstructorWithRequestAndResponseHeadersAndResponseBody() {
        throw new UnsupportedOperationException("Should not be called");
      }

      @FeignExceptionConstructor
      public DefinedConstructorWithRequestAndResponseHeadersAndResponseBody(feign.Request request,
          @ResponseHeaders Map headers,
          @ResponseBody String body) {
        this.request = request;
        this.body = body;
        this.headers = headers;
      }

      public DefinedConstructorWithRequestAndResponseHeadersAndResponseBody(TestPojo testPojo) {
        throw new UnsupportedOperationException("Should not be called");
      }

      @Override
      public feign.Request request() {
        return request;
      }

      @Override
      public Map headers() {
        return headers;
      }

      @Override
      public String body() {
        return body;
      }
    }

    class DefinedConstructorWithRequestAndResponseHeadersAndOptionalResponseBody
        extends ParametersException {
      feign.Request request;
      Optional<String> body;
      Map headers;

      public DefinedConstructorWithRequestAndResponseHeadersAndOptionalResponseBody() {
        throw new UnsupportedOperationException("Should not be called");
      }

      @FeignExceptionConstructor
      public DefinedConstructorWithRequestAndResponseHeadersAndOptionalResponseBody(
          feign.Request request,
          @ResponseHeaders Map headers,
          @ResponseBody Optional<String> body) {
        this.request = request;
        this.body = body;
        this.headers = headers;
      }

      public DefinedConstructorWithRequestAndResponseHeadersAndOptionalResponseBody(
          TestPojo testPojo) {
        throw new UnsupportedOperationException("Should not be called");
      }

      @Override
      public feign.Request request() {
        return request;
      }

      @Override
      public Map headers() {
        return headers;
      }

      @Override
      public Object body() {
        return body;
      }
    }

    class DefinedConstructorWithAnnotationForBody extends ParametersException {
      String body;

      public DefinedConstructorWithAnnotationForBody() {
        throw new UnsupportedOperationException("Should not be called");
      }

      @FeignExceptionConstructor
      public DefinedConstructorWithAnnotationForBody(@ResponseBody String body) {
        this.body = body;
      }

      @Override
      public Object body() {
        return body;
      }
    }

    class DefinedConstructorWithAnnotationForOptionalBody extends ParametersException {
      Optional<String> body;

      public DefinedConstructorWithAnnotationForOptionalBody() {
        throw new UnsupportedOperationException("Should not be called");
      }

      @FeignExceptionConstructor
      public DefinedConstructorWithAnnotationForOptionalBody(@ResponseBody Optional<String> body) {
        this.body = body;
      }

      @Override
      public Object body() {
        return body;
      }
    }

    class DefinedConstructorWithAnnotationForNonSupportedBody extends ParametersException {
      TestPojo body;

      @FeignExceptionConstructor
      public DefinedConstructorWithAnnotationForNonSupportedBody(@ResponseBody TestPojo body) {
        this.body = body;
      }

      @Override
      public Object body() {
        return body;
      }
    }

    class DefinedConstructorWithAnnotationForBodyAndHeaders extends ParametersException {
      String body;
      Map<String, Collection<String>> headers;

      @FeignExceptionConstructor
      public DefinedConstructorWithAnnotationForBodyAndHeaders(@ResponseBody String body,
          @ResponseHeaders Map<String, Collection<String>> headers) {
        this.body = body;
        this.headers = headers;
      }

      @Override
      public Object body() {
        return body;
      }

      @Override
      public Map<String, Collection<String>> headers() {
        return headers;
      }
    }

    class DefinedConstructorWithAnnotationForBodyAndHeadersSecondOrder extends ParametersException {
      String body;
      Map<String, Collection<String>> headers;

      @FeignExceptionConstructor
      public DefinedConstructorWithAnnotationForBodyAndHeadersSecondOrder(
          @ResponseHeaders Map<String, Collection<String>> headers, @ResponseBody String body) {
        this.body = body;
        this.headers = headers;
      }

      @Override
      public Object body() {
        return body;
      }

      @Override
      public Map<String, Collection<String>> headers() {
        return headers;
      }
    }

    class DefinedConstructorWithAnnotationForHeaders extends ParametersException {
      Map<String, Collection<String>> headers;

      @FeignExceptionConstructor
      public DefinedConstructorWithAnnotationForHeaders(
          @ResponseHeaders Map<String, Collection<String>> headers) {
        this.headers = headers;
      }

      @Override
      public Map<String, Collection<String>> headers() {
        return headers;
      }
    }

    class DefinedConstructorWithAnnotationForHeadersButNotForBody extends ParametersException {
      String body;
      Map<String, Collection<String>> headers;

      @FeignExceptionConstructor
      public DefinedConstructorWithAnnotationForHeadersButNotForBody(
          @ResponseHeaders Map<String, Collection<String>> headers, String body) {
        this.body = body;
        this.headers = headers;
      }

      @Override
      public Object body() {
        return body;
      }

      @Override
      public Map<String, Collection<String>> headers() {
        return headers;
      }
    }
  }
}
