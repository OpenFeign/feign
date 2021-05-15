package feign;

import feign.InvocationHandlerFactory.MethodHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

public class SharedParameters {

  private final Map<String, Object> paramNameToValue = new HashMap<>();

  public ConfigurationMethodHandler newHandler(final MethodMetadata methodMetadata) {
    return new ConfigurationMethodHandler(methodMetadata);
  }

  public Map<String, Object> asMap() {
    return paramNameToValue;
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
              .forEach(name -> paramNameToValue.put(name, argv[index])));
      return null;
    }
  }
}
