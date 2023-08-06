package feign;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;

/**
 * An implementation of {@link ResponseInterceptor} the returns the value of the location header when appropriate.
 *
 * This implementation will return Collections, Strings, types that can be constructed from those
 */
public class RedirectionInterceptor implements ResponseInterceptor {
	@Override
	public Object aroundDecode(InvocationContext invocationContext) throws Exception {
		Response response = invocationContext.response();
		int status = response.status();
		Object returnValue = null;
		if (300 <= status && status < 400) {
			Type returnType = rawType(invocationContext.returnType());
			Collection<String> locations = response.headers().getOrDefault("Location", Collections.emptyList());
			if (Collection.class.equals(returnType)) {
				returnValue = locations;
			}
			else if (String.class.equals(returnType)) {
				if (locations.isEmpty()) {
					returnValue = "";
				}
				else {
					returnValue = locations.stream().findFirst().get();
				}
			}
		}
		if (returnValue == null) {
			return invocationContext.proceed();
		}
		else {
			response.close();
			return returnValue;
		}
	}

	private Type rawType(Type type) {
		return type instanceof ParameterizedType ? ((ParameterizedType) type).getRawType() : type;
	}
}
