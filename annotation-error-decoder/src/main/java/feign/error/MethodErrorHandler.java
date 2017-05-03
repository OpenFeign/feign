package feign.error;

import feign.Response;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

class MethodErrorHandler {

    private final Map<Integer, ExceptionGenerator> methodLevelExceptionsByCode;
    private final Map<Integer, ExceptionGenerator> classLevelExceptionsByCode;
    private final ExceptionGenerator defaultException;

    MethodErrorHandler(Map<Integer, ExceptionGenerator> methodLevelExceptionsByCode,
                       Map<Integer, ExceptionGenerator> classLevelExceptionsByCode,
                       ExceptionGenerator defaultException) {
        this.methodLevelExceptionsByCode = methodLevelExceptionsByCode;
        this.classLevelExceptionsByCode = classLevelExceptionsByCode;
        this.defaultException = defaultException;
    }


    public Exception decode(Response response) {
        ExceptionGenerator constructorDefinition = getConstructorDefinition(response);
        return createException(constructorDefinition, response);
    }

    private ExceptionGenerator getConstructorDefinition(Response response) {
        if(methodLevelExceptionsByCode.containsKey(response.status())) {
            return methodLevelExceptionsByCode.get(response.status());
        }
        if(classLevelExceptionsByCode.containsKey(response.status())) {
            return classLevelExceptionsByCode.get(response.status());
        }
        return defaultException;
    }

    protected Exception createException(ExceptionGenerator constructorDefinition, Response response) {
        try {
            return constructorDefinition.createException(response);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access constructor", e);
        } catch (InstantiationException e) {
            throw new IllegalStateException("Cannot instantiate exception with constructor", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Cannot invoke constructor", e);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Constructor does not exist", e);
        }
    }
}
