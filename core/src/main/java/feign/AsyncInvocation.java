package feign;

import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;

/**
 * A specific invocation of an APU
 */
class AsyncInvocation<C> {
	
	private final C context;
	private final String configKey;
	private final Type underlyingType;
	private final long startNanos;
	private CompletableFuture<Response> responseFuture;
	
	AsyncInvocation(C context, String configKey, Type underlyingType) {
		super();
		this.context = context;
		this.configKey = configKey;
		this.underlyingType = underlyingType;
		this.startNanos = System.nanoTime();
	}

	C context() {
		return context;
	}

	String configKey() {
		return configKey;
	}

	long startNanos() {
		return startNanos;
	}
	
	Type underlyingType() {
		return underlyingType;
	}
	
	void setResponseFuture(CompletableFuture<Response> responseFuture) {
		this.responseFuture = responseFuture;		
	}
	
	CompletableFuture<Response> responseFuture() {
		return responseFuture;
	}
}
