package feign.error;

import feign.Response;
import feign.Util;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

class MethodErrorHandler {

    private final Map<Integer, ExceptionConstructorDefinition> methodLevelExceptionsByCode;
    private final Map<Integer, ExceptionConstructorDefinition> classLevelExceptionsByCode;
    private final ExceptionConstructorDefinition defaultException;

    MethodErrorHandler(Map<Integer, ExceptionConstructorDefinition> methodLevelExceptionsByCode,
                       Map<Integer, ExceptionConstructorDefinition> classLevelExceptionsByCode,
                       ExceptionConstructorDefinition defaultException) {
        this.methodLevelExceptionsByCode = methodLevelExceptionsByCode;
        this.classLevelExceptionsByCode = classLevelExceptionsByCode;
        this.defaultException = defaultException;
    }


    public Exception decode(Response response) {
        ExceptionConstructorDefinition constructorDefinition = getConstructorDefinition(response);
        return createException(constructorDefinition, response);
    }

    private ExceptionConstructorDefinition getConstructorDefinition(Response response) {
        if(methodLevelExceptionsByCode.containsKey(response.status())) {
            return methodLevelExceptionsByCode.get(response.status());
        }
        if(classLevelExceptionsByCode.containsKey(response.status())) {
            return classLevelExceptionsByCode.get(response.status());
        }
        return defaultException;
    }

    protected Exception createException(ExceptionConstructorDefinition constructorDefinition, Response response) {
        try {
            switch (constructorDefinition.getConstructorType()) {
                case STATUS:
                    return constructorDefinition.getConstructor().newInstance(response.status());
                case STATUS_AND_REASON:
                    return constructorDefinition.getConstructor().newInstance(response.status(), response.reason());
                case STATUS_REASON_AND_BODY:
                    try {
                        return constructorDefinition.getConstructor().newInstance(response.status(), response.reason(), Util.toString(response.body().asReader()));
                    } catch (IOException e) {
                        return constructorDefinition.getConstructor().newInstance(response.status(), response.reason(), "Cannot read body - cause:" + e.getMessage());
                    }
                case RESPONSE:
                    return constructorDefinition.getConstructor().newInstance(response);
                case EMPTY:
                    return constructorDefinition.getConstructor().newInstance();
                default:
                    throw new IllegalStateException("Unknown constructor type [" + constructorDefinition.getConstructorType() + "]");
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access [" + constructorDefinition.getConstructorType() + "] constructor", e);
        } catch (InstantiationException e) {
            throw new IllegalStateException("Cannot instantiate exception with [" + constructorDefinition.getConstructorType() + "] constructor", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Cannot invoke [" + constructorDefinition.getConstructorType() + "] constructor", e);
        }
    }
}
