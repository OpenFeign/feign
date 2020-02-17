
package feign;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;


public class ReflectiveAsyncFeign<C> extends AsyncFeign<C> {

	private static class MethodInfo {
		private final String configKey;
		private final Type underlyingType;

		MethodInfo(Class<?> targetType, Method method) {
			this.configKey = Feign.configKey(targetType, method);

			Type type = method.getGenericReturnType();
		
			this.underlyingType = ((ParameterizedType) type).getActualTypeArguments()[0];
		}
	}

	private class AsyncFeignInvocationHandler<T> implements InvocationHandler {

		private final Map<Method, MethodInfo> methodInfoLookup = new ConcurrentHashMap<>();

		private final Class<T> type;
		private final T instance;
		private final C context;

		AsyncFeignInvocationHandler(Class<T> type, T instance, C context) {
			this.type = type;
			this.instance = instance;
			this.context = context;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			if ("equals".equals(method.getName()) && method.getParameterCount() == 1) {
				try {
					Object otherHandler = args.length > 0 && args[0] != null ? Proxy.getInvocationHandler(args[0])
							: null;
					return equals(otherHandler);
				} catch (IllegalArgumentException e) {
					return false;
				}
			} else if ("hashCode".equals(method.getName()) && method.getParameterCount() == 0) {
				return hashCode();
			} else if ("toString".equals(method.getName()) && method.getParameterCount() == 0) {
				return toString();
			}

			MethodInfo methodInfo = methodInfoLookup.computeIfAbsent(method, m -> new MethodInfo(type, m));

			setInvocationContext(new AsyncInvocation<C>(context, methodInfo.configKey, methodInfo.underlyingType));
			try {
				return method.invoke(instance, args);
			} catch(InvocationTargetException e) {
				// unwrap
				throw e.getCause();
			} finally {
				clearInvocationContext();
			}
		}

		@SuppressWarnings("unchecked")
		@Override
		public boolean equals(Object obj) {
			if (obj instanceof AsyncFeignInvocationHandler) {
				AsyncFeignInvocationHandler<?> other = (AsyncFeignInvocationHandler<?>) obj;
				return instance.equals(other.instance);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return instance.hashCode();
		}

		@Override
		public String toString() {
			return instance.toString();
		}
	}

	public ReflectiveAsyncFeign(AsyncBuilder<C> asyncBuilder) {
		super(asyncBuilder);
	}
	
	private String getFullMethodName(Class<?> type, Type retType, Method m) {
		return retType.getTypeName() + " " + type.toGenericString() + "." + m.getName();
	}

	@Override
	protected <T> T wrap(Class<T> type, T instance, C context) {
		if (!type.isInterface())
			throw new IllegalArgumentException("Type must be an interface: " + type);

		for (Method m : type.getMethods()) {
			Type retType = m.getGenericReturnType();
			if (m.getReturnType() != CompletableFuture.class)
				throw new IllegalArgumentException("Method return type is not CompleteableFuture: "
						+ getFullMethodName(type, retType, m));
			
			if (!ParameterizedType.class.isInstance(retType))
				throw new IllegalArgumentException("Method return type is not parameterized: "
						+ getFullMethodName(type, retType, m));
			
			if (WildcardType.class.isInstance(ParameterizedType.class.cast(retType).getActualTypeArguments()[0]))
				throw new IllegalArgumentException("Wildcards are not supported for return-type parameters: "
						+ getFullMethodName(type, retType, m));
		}

		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
							new AsyncFeignInvocationHandler<>(type, instance, context)));
	}
}
