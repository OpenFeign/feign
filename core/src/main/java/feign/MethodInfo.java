package feign;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;

class MethodInfo {
  private final String configKey;
  private final Type underlyingReturnType;
  private final boolean asyncReturnType;

  MethodInfo(String configKey, Type underlyingReturnType, boolean asyncReturnType) {
    this.configKey = configKey;
    this.underlyingReturnType = underlyingReturnType;
    this.asyncReturnType = asyncReturnType;
  }

  MethodInfo(Class<?> targetType, Method method) {
    this.configKey = Feign.configKey(targetType, method);

    Type type = method.getGenericReturnType();

    if (method.getReturnType() != CompletableFuture.class) {
      this.asyncReturnType = false;
      this.underlyingReturnType = type;
    }
    else {
      this.asyncReturnType = true;
      this.underlyingReturnType = ((ParameterizedType) type).getActualTypeArguments()[0];
    }
  }

  String configKey() { return configKey; }

  Type underlyingReturnType() { return underlyingReturnType; }

  boolean isAsyncReturnType() { return asyncReturnType; }
}
