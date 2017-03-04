package feign.error;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.Parameter;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static feign.error.AnnotationErrorDecoderExceptionConstructorsTest.TestClientInterfaceWithDifferentExceptionConstructors;
import static feign.error.AnnotationErrorDecoderExceptionConstructorsTest.TestClientInterfaceWithDifferentExceptionConstructors.*;

@RunWith(Parameterized.class)
public class AnnotationErrorDecoderExceptionConstructorsTest extends AbstractAnnotationErrorDecoderTest<TestClientInterfaceWithDifferentExceptionConstructors> {


    private static final String NO_BODY = "NO BODY";
    private static final Object NULL_BODY = null;
    private static final String NON_NULL_BODY = "A GIVEN BODY";
    private static final Map<String, Collection<String>> NON_NULL_HEADERS = new HashMap<String, Collection<String>>();
    private static final Map<String, Collection<String>> NO_HEADERS = null;


    @Override
    public Class<TestClientInterfaceWithDifferentExceptionConstructors> interfaceAtTest() {
        return TestClientInterfaceWithDifferentExceptionConstructors.class;
    }

    @Parameters(name = "{0}: When error code ({1}) on method ({2}) should return exception type ({3})")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {"Test Default Constructor",  500, DefaultConstructorException.class, NO_BODY, NO_HEADERS},
            {"test Default Constructor",  501, DeclaredDefaultConstructorException.class, NO_BODY, NO_HEADERS},
            {"test Default Constructor",  502, DeclaredDefaultConstructorWithOtherConstructorsException.class, NO_BODY, NO_HEADERS},
            {"test Declared Constructor", 503, DefinedConstructorWithNoAnnotationForBody.class, NON_NULL_BODY, NO_HEADERS},
            {"test Declared Constructor", 504, DefinedConstructorWithAnnotationForBody.class, NON_NULL_BODY, NO_HEADERS},
            {"test Declared Constructor", 505, DefinedConstructorWithAnnotationForBodyAndHeaders.class, NON_NULL_BODY, NON_NULL_HEADERS },
            {"test Declared Constructor", 506, DefinedConstructorWithAnnotationForBodyAndHeadersSecondOrder.class, NON_NULL_BODY, NON_NULL_HEADERS },
            {"test Declared Constructor", 507, DefinedConstructorWithAnnotationForHeaders.class, NO_BODY, NON_NULL_HEADERS },
            {"test Declared Constructor", 508, DefinedConstructorWithAnnotationForHeadersButNotForBody.class, NON_NULL_BODY, NON_NULL_HEADERS },
            {"test Declared Constructor", 509, DefinedConstructorWithAnnotationForNonSupportedBody.class, NULL_BODY, NO_HEADERS},
        });
    }

    @Parameter // first data value (0) is default
    public String testName;

    @Parameter(1)
    public int errorCode;

    @Parameter(2)
    public Class<? extends Exception> expectedExceptionClass;

    @Parameter(3)
    public Object expectedBody;

    @Parameter(4)
    public Map<String, Collection<String>> expectedHeaders;

    @Test
    public void test() throws Exception {

        AnnotationErrorDecoder decoder = AnnotationErrorDecoder.builderFor( TestClientInterfaceWithDifferentExceptionConstructors.class ).build();

        Exception genericException = decoder.decode(feignConfigKey("method1Test"),
            testResponse(errorCode, NON_NULL_BODY, NON_NULL_HEADERS));

        assertThat(genericException.getClass()).isEqualTo(expectedExceptionClass);

        ParametersException exception = (ParametersException) genericException;
        assertThat(exception.body()).isEqualTo(expectedBody);
        assertThat(exception.headers()).isEqualTo(expectedHeaders);


    }

    interface TestClientInterfaceWithDifferentExceptionConstructors {

        @ErrorHandling(codeSpecific =
            {
                @ErrorCodes( codes = {500}, generate = DefaultConstructorException.class ),
                @ErrorCodes( codes = {501}, generate = DeclaredDefaultConstructorException.class),
                @ErrorCodes( codes = {502}, generate = DeclaredDefaultConstructorWithOtherConstructorsException.class ),
                @ErrorCodes( codes = {503}, generate = DefinedConstructorWithNoAnnotationForBody.class ),
                @ErrorCodes( codes = {504}, generate = DefinedConstructorWithAnnotationForBody.class ),
                @ErrorCodes( codes = {505}, generate = DefinedConstructorWithAnnotationForBodyAndHeaders.class ),
                @ErrorCodes( codes = {506}, generate = DefinedConstructorWithAnnotationForBodyAndHeadersSecondOrder.class ),
                @ErrorCodes( codes = {507}, generate = DefinedConstructorWithAnnotationForHeaders.class ),
                @ErrorCodes( codes = {508}, generate = DefinedConstructorWithAnnotationForHeadersButNotForBody.class ),
                @ErrorCodes( codes = {509}, generate = DefinedConstructorWithAnnotationForNonSupportedBody.class )

            }
        )
        void method1Test();

        class ParametersException extends Exception {
            public Object body() {
                return NO_BODY;
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
            public DeclaredDefaultConstructorWithOtherConstructorsException(String message, Throwable cause) {
                throw new UnsupportedOperationException("Should not be called");
            }
        }

        class DefinedConstructorWithNoAnnotationForBody extends ParametersException {
            public String body;
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

        class DefinedConstructorWithAnnotationForBody extends ParametersException {
            public String body;
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

        class DefinedConstructorWithAnnotationForNonSupportedBody extends ParametersException {
            public TestPojo body;
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
            public String body;
            public Map<String, Collection<String>> headers;

            @FeignExceptionConstructor
            public DefinedConstructorWithAnnotationForBodyAndHeaders(@ResponseBody String body, @ResponseHeaders Map<String, Collection<String>> headers ) {
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
            public String body;
            public Map<String, Collection<String>> headers;

            @FeignExceptionConstructor
            public DefinedConstructorWithAnnotationForBodyAndHeadersSecondOrder(@ResponseHeaders Map<String, Collection<String>> headers, @ResponseBody String body ) {
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
            public Map<String, Collection<String>> headers;

            @FeignExceptionConstructor
            public DefinedConstructorWithAnnotationForHeaders(@ResponseHeaders Map<String, Collection<String>> headers ) {
                this.headers = headers;
            }
            @Override
            public Map<String, Collection<String>> headers() {
                return headers;
            }
        }

        class DefinedConstructorWithAnnotationForHeadersButNotForBody extends ParametersException {
            public String body;
            public Map<String, Collection<String>> headers;

            @FeignExceptionConstructor
            public DefinedConstructorWithAnnotationForHeadersButNotForBody(@ResponseHeaders Map<String, Collection<String>> headers, String body ) {
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
