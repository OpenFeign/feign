package feign;

public interface FeignFactory {

    Feign create(ReflectiveFeign.ParseHandlersByName targetToHandlersByName, InvocationHandlerFactory factory);

    static class Factory implements FeignFactory {

        @Override
        public Feign create(ReflectiveFeign.ParseHandlersByName handlersByName, InvocationHandlerFactory invocationHandlerFactory) {
            return new ReflectiveFeign(handlersByName, invocationHandlerFactory);
        }
    }
}
