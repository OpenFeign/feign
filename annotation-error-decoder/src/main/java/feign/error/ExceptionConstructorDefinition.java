package feign.error;

import feign.Response;

import java.lang.reflect.Constructor;

class ExceptionConstructorDefinition {


    enum ConstructorType {
        RESPONSE,
        STATUS,
        STATUS_AND_REASON,
        STATUS_REASON_AND_BODY,
        EMPTY
    }

    private final Constructor<? extends Exception> constructor;
    private final ConstructorType constructorType;
    private final Class<? extends Exception> exceptionType;

    private ExceptionConstructorDefinition(Constructor<? extends Exception> constructor, ConstructorType constructorType, Class<? extends Exception> exceptionType) {
        this.constructor = constructor;
        this.constructorType = constructorType;
        this.exceptionType = exceptionType;
    }

    Constructor<? extends Exception> getConstructor() {
        return constructor;
    }

    ConstructorType getConstructorType() {
        return constructorType;
    }

    Class<? extends Exception> getExceptionType() {
        return exceptionType;
    }

    static ExceptionConstructorDefinition createExceptionConstructorDefinition(Class<? extends Exception> exceptionClass) {
        try {
            Constructor<? extends Exception> constructor = exceptionClass.getConstructor(Response.class);
            return new ExceptionConstructorDefinition(constructor, ExceptionConstructorDefinition.ConstructorType.RESPONSE, exceptionClass);
        } catch (NoSuchMethodException e0) {
            try {
                Constructor<? extends Exception> constructor = exceptionClass.getConstructor(Integer.TYPE, String.class, String.class);
                return new ExceptionConstructorDefinition(constructor, ExceptionConstructorDefinition.ConstructorType.STATUS_REASON_AND_BODY, exceptionClass);
            } catch (NoSuchMethodException e1) {
                try {
                    Constructor<? extends Exception> constructor = exceptionClass.getConstructor(Integer.class, String.class, String.class);
                    return new ExceptionConstructorDefinition(constructor, ExceptionConstructorDefinition.ConstructorType.STATUS_REASON_AND_BODY, exceptionClass);
                } catch (NoSuchMethodException e2) {
                    try {
                        Constructor<? extends Exception> constructor = exceptionClass.getConstructor(Integer.TYPE, String.class);
                        return new ExceptionConstructorDefinition(constructor, ExceptionConstructorDefinition.ConstructorType.STATUS_AND_REASON, exceptionClass);
                    } catch (NoSuchMethodException e3) {
                        try {
                            Constructor<? extends Exception> constructor = exceptionClass.getConstructor(Integer.class, String.class);
                            return new ExceptionConstructorDefinition(constructor, ExceptionConstructorDefinition.ConstructorType.STATUS_AND_REASON, exceptionClass);
                        }
                        catch (NoSuchMethodException e4) {
                            try {
                                Constructor<? extends Exception> constructor = exceptionClass.getConstructor(Response.class);
                                return new ExceptionConstructorDefinition(constructor, ExceptionConstructorDefinition.ConstructorType.RESPONSE, exceptionClass);
                            } catch (NoSuchMethodException e5) {
                                try {
                                    Constructor<? extends Exception> constructor = exceptionClass.getConstructor();
                                    return new ExceptionConstructorDefinition(constructor, ExceptionConstructorDefinition.ConstructorType.EMPTY, exceptionClass);
                                } catch (NoSuchMethodException e6) {
                                    throw new IllegalStateException("Cannot find appropriate/usable constructor for exception [" + exceptionClass.getName() + "]");
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
