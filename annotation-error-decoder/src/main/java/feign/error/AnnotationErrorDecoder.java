package feign.error;

import feign.Response;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static feign.Feign.configKey;

public class AnnotationErrorDecoder implements ErrorDecoder {

    private final Map<String, MethodErrorHandler> errorHandlerMap;
    private final ErrorDecoder defaultDecoder;


    AnnotationErrorDecoder(Map<String, MethodErrorHandler> errorHandlerMap, ErrorDecoder defaultDecoder) {
        this.errorHandlerMap = errorHandlerMap;
        this.defaultDecoder = defaultDecoder;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        if(errorHandlerMap.containsKey(methodKey)) {
            return errorHandlerMap.get(methodKey).decode(response);
        }
        return defaultDecoder.decode(methodKey, response);
    }


    public static AnnotationErrorDecoder.Builder builderFor(Class<?> apiType) {
        return new Builder(apiType);
    }

    public static class Builder {
        private final Class<?> apiType;
        private ErrorDecoder defaultDecoder = new ErrorDecoder.Default();
        private Decoder responseBodyDecoder = new Decoder.Default();


        public Builder(Class<?> apiType) {
            this.apiType = apiType;
        }

        public Builder withDefaultDecoder(ErrorDecoder defaultDecoder) {
            this.defaultDecoder = defaultDecoder;
            return this;
        }

        public AnnotationErrorDecoder build() {
            Map<String, MethodErrorHandler> errorHandlerMap = generateErrorHandlerMapFromApi(apiType);
            return new AnnotationErrorDecoder(errorHandlerMap, defaultDecoder);
        }

        Map<String, MethodErrorHandler> generateErrorHandlerMapFromApi(Class<?> apiType) {

            ExceptionGenerator classLevelDefault = new ExceptionGenerator.Builder()
                .withResponseBodyDecoder(responseBodyDecoder)
                .withExceptionType(ErrorHandling.NO_DEFAULT.class)
                .build();
            Map<Integer, ExceptionGenerator> classLevelStatusCodeDefinitions = new HashMap<Integer, ExceptionGenerator>();

            if(apiType.isAnnotationPresent(ErrorHandling.class)) {
                ErrorHandlingDefinition classErrorHandlingDefinition = readAnnotation(apiType.getAnnotation(ErrorHandling.class), responseBodyDecoder);
                classLevelDefault = classErrorHandlingDefinition.defaultThrow;
                classLevelStatusCodeDefinitions = classErrorHandlingDefinition.statusCodesMap;
            }

            Map<String, MethodErrorHandler> methodErrorHandlerMap = new HashMap<String, MethodErrorHandler>();
            for(Method method : apiType.getMethods()) {
                if(method.isAnnotationPresent(ErrorHandling.class)) {
                    ErrorHandlingDefinition methodErrorHandling = readAnnotation(method.getAnnotation(ErrorHandling.class), responseBodyDecoder);
                    ExceptionGenerator methodDefault = methodErrorHandling.defaultThrow;
                    if(methodDefault.getExceptionType().equals(ErrorHandling.NO_DEFAULT.class)) {
                        methodDefault = classLevelDefault;
                    }

                    MethodErrorHandler methodErrorHandler =
                        new MethodErrorHandler(methodErrorHandling.statusCodesMap, classLevelStatusCodeDefinitions, methodDefault );

                    methodErrorHandlerMap.put(configKey(apiType, method), methodErrorHandler);
                }
            }

            return methodErrorHandlerMap;
        }

        static ErrorHandlingDefinition readAnnotation(ErrorHandling errorHandling, Decoder responseBodyDecoder) {
            ExceptionGenerator defaultException = new ExceptionGenerator.Builder()
                .withResponseBodyDecoder(responseBodyDecoder)
                .withExceptionType(errorHandling.defaultException())
                .build();
            Map<Integer, ExceptionGenerator> statusCodesDefinition = new HashMap<Integer, ExceptionGenerator>();

            for(ErrorCodes statusCodeDefinition : errorHandling.codeSpecific()) {
                for(int statusCode : statusCodeDefinition.codes()) {
                    if(statusCodesDefinition.containsKey(statusCode)) {
                        throw new IllegalStateException(
                            "Status Code [" + statusCode + "] " +
                                "has already been declared to throw [" + statusCodesDefinition.get(statusCode).getExceptionType().getName() + "] " +
                                "and [" + statusCodeDefinition.generate() + "] - dupe definition");
                    }
                    statusCodesDefinition.put(statusCode,
                        new ExceptionGenerator.Builder()
                            .withResponseBodyDecoder(responseBodyDecoder)
                            .withExceptionType(statusCodeDefinition.generate())
                            .build());
                }
            }

            return new ErrorHandlingDefinition(defaultException, statusCodesDefinition);
        }

        private static class ErrorHandlingDefinition {
            private final ExceptionGenerator defaultThrow;
            private final Map<Integer, ExceptionGenerator> statusCodesMap;


            private ErrorHandlingDefinition(ExceptionGenerator defaultThrow, Map<Integer, ExceptionGenerator> statusCodesMap) {
                this.defaultThrow = defaultThrow;
                this.statusCodesMap = statusCodesMap;
            }
        }
    }
}
