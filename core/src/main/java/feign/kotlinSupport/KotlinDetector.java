package feign.kotlinSupport;

import kotlin.Unit;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

@SuppressWarnings("unchecked")
public abstract class KotlinDetector {
	private static final Class<? extends Annotation> kotlinMetadata;
	private static final boolean supportsKotlin;

	static {
		ClassLoader classLoader = KotlinDetector.class.getClassLoader();
		Class<?> metadata;
		try {
			metadata = ClassUtils.forName("kotlin.Metadata", classLoader);
		}
		catch (ClassNotFoundException ex) {
			// Kotlin API not available - no Kotlin support
			metadata = null;
		}
		kotlinMetadata = (Class<? extends Annotation>) metadata;
		supportsKotlin = ClassUtils.isPresent("feign.kotlin.MethodKt", classLoader);
	}

	/**
	 * Return {@code true} if the method is a suspending function.
	 * @since 5.3
	 */
	public static boolean isSuspendingFunction(Method method) {
		if (KotlinDetector.isKotlinType(method.getDeclaringClass())) {
			Class<?>[] types = method.getParameterTypes();
			if (types.length > 0 && "kotlin.coroutines.Continuation".equals(types[types.length - 1].getName())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Determine whether the given {@code Class} is a Kotlin type
	 * (with Kotlin metadata present on it).
	 */
	private static boolean isKotlinType(Class<?> clazz) {
		return (supportsKotlin && clazz.getDeclaredAnnotation(kotlinMetadata) != null);
	}

	public static boolean isUnitType(Type type) {
		return supportsKotlin && Unit.class == type;
	}
}
