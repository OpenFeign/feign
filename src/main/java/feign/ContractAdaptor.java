package feign;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Fixture for difference of {@link Contract} interface between different Feign versions.
 *
 * @author Alexei KLENIN
 */
public final class ContractAdaptor {
  private static final Method parseAndValidateMetadataMethod = getParseAndValidateMetadata();

  private ContractAdaptor() {
  }

  @SuppressWarnings("unchecked")
  static public List<MethodMetadata> parseAndValidateMetadata(Contract contract, Class<?> var1) {
    try {
      return (List<MethodMetadata>) parseAndValidateMetadataMethod.invoke(contract, var1);
    } catch (IllegalAccessException accessException) {
      throw new RuntimeException(accessException);
    } catch (InvocationTargetException methodException) {
      throw (RuntimeException) methodException.getTargetException();
    }
  }

  static Method getParseAndValidateMetadata() {
    try {
      // For Feign >= 10.6.0
      return Contract.class.getDeclaredMethod("parseAndValidateMetadata", Class.class);
    } catch (NoSuchMethodException noMethodException) {
      // For Feign < 10.6.0
      try {
        return Contract.class.getDeclaredMethod("parseAndValidatateMetadata", Class.class);
      } catch (NoSuchMethodException noOldMethodException) {
        throw new RuntimeException(noMethodException);
      }
    }
  }
}
