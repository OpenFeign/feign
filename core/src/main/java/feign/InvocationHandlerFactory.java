package feign;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

public interface InvocationHandlerFactory {
    public InvocationHandler create(Target target, Map<Method, MethodHandler> methodToHandler);

    public static class Default implements InvocationHandlerFactory {

        @Override
        public InvocationHandler create(Target target, Map<Method, MethodHandler> methodToHandler) {
            return new FeignInvocationHandler(target, methodToHandler);
        }
    }
}
