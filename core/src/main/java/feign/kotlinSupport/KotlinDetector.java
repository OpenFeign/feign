package feign.kotlinSupport;

import kotlin.Unit;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

@SuppressWarnings("unchecked")
public abstract class KotlinDetector {
	private static final boolean supportsKotlin;
	private static final Class<? extends Annotation> kotlinMetadata;

	static {
		ClassLoader classLoader = KotlinDetector.class.getClassLoader();
		supportsKotlin = ClassUtils.isPresent("feign.kotlin.MethodKt", classLoader);
		kotlinMetadata = supportsKotlin
			? (Class<? extends Annotation>) ClassUtils.tryGetForName("kotlin.Metadata", classLoader)
			: null;
	}

	public static boolean isSuspendingFunction(Method method) {
		if (!KotlinDetector.isKotlinType(method.getDeclaringClass())) {
			return false;
		}

		Class<?>[] params = method.getParameterTypes();
		return params.length > 0 && "kotlin.coroutines.Continuation".equals(params[params.length - 1].getName());
	}

	private static boolean isKotlinType(Class<?> clazz) {
		return supportsKotlin && clazz.getDeclaredAnnotation(kotlinMetadata) != null;
	}

	public static boolean isUnitType(Type type) {
		return supportsKotlin && Unit.class == type;
	}
}
