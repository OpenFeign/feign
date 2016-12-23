package feign.error;

import feign.Response;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.HashMap;

import static feign.Feign.configKey;
import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationErrorDecoderTest {

    private AnnotationErrorDecoder decoder;
    private boolean fallbackCalled = false;
    private Exception fallbackException = new Exception();


    @Before
    public void setUp() throws Exception {
        fallbackCalled = false;

        decoder = AnnotationErrorDecoder.builderFor( TestClientInterface.class )
            .withDefaultDecoder( (methodKey, response) -> {fallbackCalled = true;
            return fallbackException; })
            .build();
    }

    @Test
    public void decodesCodeSpecificAtMethod() throws Exception {
        assertThat(decoder.decode(feignConfigKey("method1Test"), testResponse(404)).getClass())
            .isEqualTo(Method1NotFoundException.class);
        assertThat(decoder.decode(feignConfigKey("method1Test"), testResponse(401)).getClass())
            .isEqualTo(UnauthenticatedOrUnautherizedException.class);
        assertThat(decoder.decode(feignConfigKey("method2Test"), testResponse(404)).getClass())
            .isEqualTo(Method2NotFoundException.class);
        assertThat(decoder.decode(feignConfigKey("method2Test"), testResponse(500)).getClass())
            .isEqualTo(ServeErrorException.class);
        assertThat(decoder.decode(feignConfigKey("method2Test"), testResponse(503)).getClass())
            .isEqualTo(ServeErrorException.class);
    }

    @Test
    public void decodesCodeSpecificAtClass() throws Exception {
        assertThat(decoder.decode(feignConfigKey("method1Test"), testResponse(403)).getClass())
            .isEqualTo(UnauthenticatedOrUnautherizedException.class);
        assertThat(decoder.decode(feignConfigKey("method2Test"), testResponse(403)).getClass())
            .isEqualTo(UnauthenticatedOrUnautherizedException.class);
        assertThat(decoder.decode(feignConfigKey("method3Test"), testResponse(404)).getClass())
            .isEqualTo(ClassLevelNotFoundException.class);
        assertThat(decoder.decode(feignConfigKey("method3Test"), testResponse(403)).getClass())
            .isEqualTo(UnauthenticatedOrUnautherizedException.class);
    }

    @Test
    public void decodesDefaultAtMethod() throws Exception {
        assertThat(decoder.decode(feignConfigKey("method1Test"), testResponse(504)).getClass())
            .isEqualTo(Method1DefaultException.class);
        assertThat(decoder.decode(feignConfigKey("method3Test"), testResponse(504)).getClass())
            .isEqualTo(Method3DefaultException.class);
    }

    @Test
    public void decodesDefaultAtClass() throws Exception {
        assertThat(decoder.decode(feignConfigKey("method2Test"), testResponse(504)).getClass())
            .isEqualTo(ClassLevelDefaultException.class);
    }

    @Test
    public void fallbackToDefaultDecoder() throws Exception {
        decoder = AnnotationErrorDecoder.builderFor( TestClientWithNoDefaultErrorHandling.class )
            .withDefaultDecoder( (methodKey, response) -> {fallbackCalled = true;
                return fallbackException; })
            .build();

        assertThat(decoder.decode(feignConfigKey(TestClientWithNoDefaultErrorHandling.class, "method1Test"), testResponse(403)).getClass())
            .isEqualTo(UnauthenticatedOrUnautherizedException.class);
        assertThat(decoder.decode(feignConfigKey(TestClientWithNoDefaultErrorHandling.class, "method1Test"), testResponse(503)))
            .isEqualTo(fallbackException);
        assertThat(fallbackCalled).isTrue();

    }

    private static String feignConfigKey(Class apiType, String methodName) throws NoSuchMethodException {
        return configKey(apiType, TestClientInterface.class.getMethod(methodName));
    }

    private static String feignConfigKey(String methodName) throws NoSuchMethodException {
        return feignConfigKey(TestClientInterface.class, methodName);
    }

    private static Response testResponse(int status) {
        return Response.builder()
            .status(status)
            .headers(new HashMap<String, Collection<String>>())
            .build();
    }

    @ErrorHandling(codeSpecific =
        {
            @StatusCodes( codes = {404}, generate = ClassLevelNotFoundException.class),
            @StatusCodes( codes = {403}, generate = UnauthenticatedOrUnautherizedException.class)
        }
    )
    interface TestClientWithNoDefaultErrorHandling {
        void method1Test();
    }


    @ErrorHandling(codeSpecific =
        {
            @StatusCodes( codes = {404}, generate = ClassLevelNotFoundException.class),
            @StatusCodes( codes = {403}, generate = UnauthenticatedOrUnautherizedException.class)
        },
        defaultException = ClassLevelDefaultException.class
    )
    interface TestClientInterface {
        @ErrorHandling(codeSpecific =
            {
                @StatusCodes( codes = {404}, generate = Method1NotFoundException.class ),
                @StatusCodes( codes = {401}, generate = UnauthenticatedOrUnautherizedException.class)
            }
            ,
            defaultException = Method1DefaultException.class
        )
        void method1Test();

        @ErrorHandling(codeSpecific =
            {
                @StatusCodes( codes = {404}, generate = Method2NotFoundException.class ),
                @StatusCodes( codes = {500, 503}, generate = ServeErrorException.class )
            }
        )
        void method2Test();

        @ErrorHandling(
            defaultException = Method3DefaultException.class
        )
        void method3Test();

    }

    public static class ClassLevelDefaultException extends Exception {}
    public static class Method1DefaultException extends Exception {}
    public static class Method3DefaultException extends Exception {}
    public static class Method1NotFoundException extends Exception {}
    public static class Method2NotFoundException extends Exception {}
    public static class ClassLevelNotFoundException extends Exception {}
    public static class UnauthenticatedOrUnautherizedException extends Exception {}
    public static class ServeErrorException extends Exception {}


}