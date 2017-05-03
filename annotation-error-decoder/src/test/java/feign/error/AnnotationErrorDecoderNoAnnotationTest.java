package feign.error;

import feign.Response;
import feign.codec.ErrorDecoder;
import org.junit.Test;

import static feign.error.AnnotationErrorDecoderNoAnnotationTest.*;
import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationErrorDecoderNoAnnotationTest extends AbstractAnnotationErrorDecoderTest<TestClientInterfaceWithNoAnnotations> {

    @Override
    public Class<TestClientInterfaceWithNoAnnotations> interfaceAtTest() {
        return TestClientInterfaceWithNoAnnotations.class;
    }

    @Test
    public void delegatesToDefaultErrorDecoder() throws Exception {

        ErrorDecoder defaultErrorDecoder = new ErrorDecoder() {
            @Override
            public Exception decode(String methodKey, Response response) {
                return new DefaultErrorDecoderException();
            }
        };

        AnnotationErrorDecoder decoder = AnnotationErrorDecoder.builderFor( TestClientInterfaceWithNoAnnotations.class )
            .withDefaultDecoder(defaultErrorDecoder)
            .build();
        
        assertThat(decoder.decode(feignConfigKey( "method1Test"), testResponse(502)).getClass())
            .isEqualTo(DefaultErrorDecoderException.class);
    }

    interface TestClientInterfaceWithNoAnnotations {
        void method1Test();
    }

    static class DefaultErrorDecoderException extends Exception {}

}
