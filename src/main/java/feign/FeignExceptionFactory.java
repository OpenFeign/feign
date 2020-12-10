package feign;

import feign.codec.DecodeException;

import java.lang.reflect.Constructor;

public final class FeignExceptionFactory {

  /**
   * Feign < 10.2.0 - false
   * Feign >= 10.2.0 - true
   */
  private static final boolean constructorWithStatus = isConstructorWithStatus();

  private static Constructor<FeignException> feignExceptionConstructor;
  private static Constructor<DecodeException> decodeExceptionConstructor;

  private FeignExceptionFactory() {
  }

  public static FeignException feignException(String message) {
    try {
      return constructorWithStatus
          ? feignExceptionConstructor.newInstance(-1, message)
          : feignExceptionConstructor.newInstance(message);
    } catch (Throwable instantiationException) {
      throw new IllegalStateException(
          String.format("Cannot instantiate %s", FeignException.class.getName()),
          instantiationException);
    }
  }

  public static DecodeException decodeException(String message, Throwable cause) {
    try {
      return constructorWithStatus
          ? decodeExceptionConstructor.newInstance(-1, message, cause)
          : decodeExceptionConstructor.newInstance(message, cause);
    } catch (Throwable instantiationException) {
      throw new IllegalStateException(
          String.format("Cannot instantiate %s", DecodeException.class.getName()),
          instantiationException);
    }
  }

  private static boolean isConstructorWithStatus() {
    try {
      feignExceptionConstructor = FeignException.class.getDeclaredConstructor(int.class, String.class);
      decodeExceptionConstructor = DecodeException.class.getDeclaredConstructor(
          int.class, String.class, Throwable.class);
      return true;
    } catch (NoSuchMethodException twoArgumentConstructorException) {
      try {
        feignExceptionConstructor = FeignException.class.getDeclaredConstructor(String.class);
        decodeExceptionConstructor = DecodeException.class.getDeclaredConstructor(String.class, Throwable.class);
      } catch (NoSuchMethodException unexpectedException) {
        throw new RuntimeException(unexpectedException);
      }
      return false;
    }
  }
}
