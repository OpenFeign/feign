package feign.kotlinSupport;

abstract class ClassUtils {
	public static Class<?> tryGetForName(String name, ClassLoader classLoader) {
		try {
			return Class.forName(name, false, classLoader);
		}
		catch (ClassNotFoundException | NoClassDefFoundError ex) {
			return null;
		}
	}

	public static boolean isPresent(String className, ClassLoader classLoader) {
		try {
			return tryGetForName(className, classLoader) != null;
		}
		catch (IllegalAccessError err) {
			throw new IllegalStateException("Readability mismatch in inheritance hierarchy of class [" +
					className + "]: " + err.getMessage(), err);
		}
		catch (Throwable ex) {
			return false;
		}
	}
}
