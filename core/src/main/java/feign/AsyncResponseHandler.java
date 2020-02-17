
package feign;

import static feign.FeignException.*;
import static feign.Util.*;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;

import feign.Logger.Level;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;

/**
 * The response handler that is used to provide asynchronous support on top of standard
 * response handling
 */
class AsyncResponseHandler {

	private static final long MAX_RESPONSE_BUFFER_SIZE = 8192L;

	private final Level logLevel;
	private final Logger logger;

	private final Decoder decoder;
	private final ErrorDecoder errorDecoder;
	private final boolean decode404;
	private final boolean closeAfterDecode;

	AsyncResponseHandler(Level logLevel, Logger logger, Decoder decoder, ErrorDecoder errorDecoder,
			boolean decode404, boolean closeAfterDecode) {
		super();
		this.logLevel = logLevel;
		this.logger = logger;
		this.decoder = decoder;
		this.errorDecoder = errorDecoder;
		this.decode404 = decode404;
		this.closeAfterDecode = closeAfterDecode;
	}
	
	void handleResponse(CompletableFuture<Object> resultFuture, AsyncInvocation<?> invocationContext,
			Response response, Type type, long elapsedTime) {
		// copied fairly liberally from SynchronousMethodHandler
		boolean shouldClose = true;
		try {
			if (logLevel != Level.NONE) {
				response = logger.logAndRebufferResponse(invocationContext.configKey(), logLevel, response,
						elapsedTime);
			}
			if (Response.class == type) {
				if (response.body() == null) {
					resultFuture.complete(response);
				} else if (response.body().length() == null || response.body().length() > MAX_RESPONSE_BUFFER_SIZE) {
					shouldClose = false;
					resultFuture.complete(response);
				} else {
					// Ensure the response body is disconnected
					byte[] bodyData = Util.toByteArray(response.body().asInputStream());
					resultFuture.complete(response.toBuilder().body(bodyData).build());
				}
			} else if (response.status() >= 200 && response.status() < 300) {
				if (Void.class == type) {
					resultFuture.complete(null);
				} else {
					Object result = decode(response, type);
					shouldClose = closeAfterDecode;
					resultFuture.complete(result);
				}
			} else if (decode404 && response.status() == 404 && Void.class != type) {
				Object result = decode(response, type);
				shouldClose = closeAfterDecode;
				resultFuture.complete(result);
			} else {
				resultFuture.completeExceptionally(errorDecoder.decode(invocationContext.configKey(), response));
			}
		} catch (IOException e) {
			if (logLevel != Level.NONE) {
				logger.logIOException(invocationContext.configKey(), logLevel, e, elapsedTime);
			}
			resultFuture.completeExceptionally(errorReading(response.request(), response, e));
		} catch (Exception e) {
			resultFuture.completeExceptionally(e);
		} finally {
			if (shouldClose) {
				ensureClosed(response.body());
			}
		}

	}

	Object decode(Response response, Type type) throws IOException {
		try {
			return decoder.decode(response, type);
		} catch (FeignException e) {
			throw e;
		} catch (RuntimeException e) {
			throw new DecodeException(response.status(), e.getMessage(), response.request(), e);
		}
	}
}
