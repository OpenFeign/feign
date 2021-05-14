package feign;

import feign.InvocationHandlerFactory.MethodHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

public class ConfigurationParameters {

    private final Map<String, Object> configuredParams = new HashMap<>();

    public ConfigurationMethodHandler newHandler(final MethodMetadata methodMetadata) {
        return new ConfigurationMethodHandler(methodMetadata);
    }

    public Map<String, Object> getParameterMap() {
        return configuredParams;
    }

    public class ConfigurationMethodHandler implements MethodHandler {

        private final MethodMetadata methodMetadata;

        public ConfigurationMethodHandler(final MethodMetadata methodMetadata) {
            this.methodMetadata = methodMetadata;
        }

        @Override
        public Object invoke(Object[] argv) throws Throwable {
            IntStream.range(0, argv.length)
                .forEach(index -> methodMetadata.indexToName()
                    .get(index)
                    .forEach(name -> configuredParams.put(name, argv[index])));
            return null;
        }
    }
}
