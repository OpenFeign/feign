package feign.jaxrs2;

import static java.lang.invoke.MethodType.methodType;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import feign.utils.RecComponent;
import feign.utils.RecordInvokeUtils;

public final class RecordInvokeUtilsImpl extends RecordInvokeUtils {
  @SuppressWarnings("unchecked")
  public static <T> T invokeCanonicalConstructor(Class<T> recordType, Object[] args) {
    try {
      RecComponent[] recordComponents = recordComponents(recordType, null);
      Type[] paramTypes =
          Arrays.stream(recordComponents).map(RecComponent::type).toArray(Type[]::new);
      paramTypes = Arrays.stream(paramTypes)
          .map(v -> v instanceof ParameterizedType ? (Class<?>) ((ParameterizedType) v).getRawType()
              : (Class<?>) v)
          .toArray(Class[]::new);
      MethodHandle MH_canonicalConstructor = LOOKUP
          .findConstructor(recordType, methodType(void.class, (Class<?>[]) paramTypes))
          .asType(methodType(Object.class, (Class<?>[]) paramTypes));
      return (T) MH_canonicalConstructor.invokeWithArguments(args);
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException("Could not construct type (" + recordType.getName() + ")");
    }
  }
}
