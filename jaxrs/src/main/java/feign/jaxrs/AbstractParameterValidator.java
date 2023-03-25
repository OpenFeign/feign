package feign.jaxrs;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import feign.Param.Expander;

public abstract class AbstractParameterValidator<T> implements Predicate<Object> {
	public static final class DefaultParameterExpander extends AbstractParameterValidator<Void> implements Expander {
		@Override
		public String expand(Object value) throws IllegalArgumentException {
			if (!super.test(value)) {
				throw new IllegalArgumentException();
			}
			return value != null ? value.toString() : null;
		}
	}

	@Override
	public boolean test(Object value) {
		if (value == null) {
			return true;
		}
		Class<?> clazz = value.getClass();
		ParameterizedType parameterizedType = ParameterizedType.class.cast(getClass().getGenericSuperclass());
		Class<?> allowedClass = (Class<?>) parameterizedType.getActualTypeArguments()[0];
		if (clazz.isPrimitive()) {
			return true;
		} else if (isAllowedType(allowedClass, clazz)) {
			return true;
		} else if (hasConstructor(clazz)) {
			return true;
		} else if (hasStaticMethod(clazz)) {
			return true;
		} else if (value instanceof Collection && checkCollectionType((Collection<?>) value, allowedClass)) {
			return true;
		} else {
			return false;
		}
	}

	protected boolean isAllowedType(Class<?> allowedClass, Class<?> clazz) {
		return allowedClass != Void.class && clazz == allowedClass;
	}

	protected static boolean hasConstructor(Class<?> clazz) {
		try {
			clazz.getDeclaredConstructor(String.class);
			return true;
		} catch (NoSuchMethodException e) {
			return false;
		}

	}

	protected final static boolean hasStaticMethod(Class<?> clazz) {
		Method[] methods = clazz.getDeclaredMethods();
		return Arrays.stream(methods)
				.anyMatch(v -> Modifier.isStatic(v.getModifiers()) && v.getParameters().length == 1
						&& v.getParameters()[0].getType() == String.class
						&& (v.getName().equals("valueOf") || v.getName().equals("fromString")));

	}

	protected <E> boolean checkCollectionType(Collection<E> collection, Class<?> allowedClass) {
		if (List.class.isInstance(collection) || Set.class.isInstance(collection)) {
			Class<?> clazz = collection.iterator().next().getClass();
			return clazz.isPrimitive() || isAllowedType(allowedClass, clazz) || hasConstructor(clazz)
					|| hasStaticMethod(clazz);
		} else {
			return false;
		}
	}
}
