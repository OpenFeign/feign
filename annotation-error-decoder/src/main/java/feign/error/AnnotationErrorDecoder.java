package feign.error;

import feign.Response;
import feign.codec.ErrorDecoder;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static feign.Feign.configKey;

public class AnnotationErrorDecoder implements ErrorDecoder {

    private static final ExceptionConstructorDefinition NO_DEFAULT_EXCEPTION = ExceptionConstructorDefinition.createExceptionConstructorDefinition(ErrorHandling.NO_DEFAULT.class);

    private final Map<String, MethodErrorHandler> errorHandlerMap;
    private final ErrorDecoder defaultDecoder;


    AnnotationErrorDecoder(Map<String, MethodErrorHandler> errorHandlerMap, ErrorDecoder defaultDecoder) {
        this.errorHandlerMap = errorHandlerMap;
        this.defaultDecoder = defaultDecoder;
    }

    @Override
    public Exception decode(String methodKey, Response response) {

        if(errorHandlerMap.containsKey(methodKey)) {
            Exception decoded = errorHandlerMap.get(methodKey).decode(response);
            if(!(decoded instanceof ErrorHandling.NO_DEFAULT)) {
                return decoded;
            }
        }
        return defaultDecoder.decode(methodKey, response);
    }


    public static AnnotationErrorDecoder.Builder builderFor(Class<?> apiType) {
        return new Builder(apiType);
    }

    public static class Builder {
        private final Class<?> apiType;
        private ErrorDecoder defaultDecoder = new ErrorDecoder.Default();

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

            ExceptionConstructorDefinition classLevelDefault = NO_DEFAULT_EXCEPTION;
            Map<Integer, ExceptionConstructorDefinition> classLevelStatusCodeDefinitions = new HashMap<Integer, ExceptionConstructorDefinition>();

            if(apiType.isAnnotationPresent(ErrorHandling.class)) {
                ErrorHandlingDefinition classErrorHandlingDefinition = readAnnotation(apiType.getAnnotation(ErrorHandling.class));
                classLevelDefault = classErrorHandlingDefinition.defaultThrow;
                classLevelStatusCodeDefinitions = classErrorHandlingDefinition.statusCodesMap;
            }

            Map<String, MethodErrorHandler> methodErrorHandlerMap = new HashMap<String, MethodErrorHandler>();
            for(Method method : apiType.getMethods()) {
                ExceptionConstructorDefinition methodDefault = NO_DEFAULT_EXCEPTION;
                ErrorHandlingDefinition methodErrorHandling =
                    new ErrorHandlingDefinition(methodDefault, new HashMap<Integer, ExceptionConstructorDefinition>());

                if(method.isAnnotationPresent(ErrorHandling.class)) {
                    methodErrorHandling = readAnnotation(method.getAnnotation(ErrorHandling.class));
                    methodDefault = methodErrorHandling.defaultThrow;
                    if(methodDefault.getExceptionType().equals(ErrorHandling.NO_DEFAULT.class)) {
                        methodDefault = classLevelDefault;
                    }

                }

                MethodErrorHandler methodErrorHandler =
                    new MethodErrorHandler(methodErrorHandling.statusCodesMap, classLevelStatusCodeDefinitions, methodDefault );
                methodErrorHandlerMap.put(configKey(apiType, method), methodErrorHandler);
            }

            return methodErrorHandlerMap;
        }

        static ErrorHandlingDefinition readAnnotation(ErrorHandling errorHandling) {
            ExceptionConstructorDefinition defaultException =
                ExceptionConstructorDefinition.createExceptionConstructorDefinition(errorHandling.defaultException());
            Map<Integer, ExceptionConstructorDefinition> statusCodesDefinition = new HashMap<Integer, ExceptionConstructorDefinition>();

            for(StatusCodes statusCodeDefinition : errorHandling.codeSpecific()) {
                for(int statusCode : statusCodeDefinition.codes()) {
                    if(statusCodesDefinition.containsKey(statusCode)) {
                        throw new IllegalStateException(
                            "Status Code [" + statusCode + "] " +
                                "has already been declared to throw [" + statusCodesDefinition.get(statusCode).getExceptionType().getName() + "] " +
                                "and [" + statusCodeDefinition.generate() + "] - dupe definition");
                    }
                    statusCodesDefinition.put(statusCode,
                        ExceptionConstructorDefinition.createExceptionConstructorDefinition(statusCodeDefinition.generate()));
                }
            }

            return new ErrorHandlingDefinition(defaultException, statusCodesDefinition);
        }

        private static class ErrorHandlingDefinition {
            private final ExceptionConstructorDefinition defaultThrow;
            private final Map<Integer, ExceptionConstructorDefinition> statusCodesMap;


            private ErrorHandlingDefinition(ExceptionConstructorDefinition defaultThrow, Map<Integer, ExceptionConstructorDefinition> statusCodesMap) {
                this.defaultThrow = defaultThrow;
                this.statusCodesMap = statusCodesMap;
            }
        }
    }
}
