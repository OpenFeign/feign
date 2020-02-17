/**
 * 
 */
package feign;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import feign.Request.Options;

/**
 * Submits HTTP {@link Request requests} asynchronously, with an optional context.
 */
public interface AsyncClient<C> {

	/**
	 * Executes the request asynchronously. Calling {@link CompletableFuture#cancel(boolean)} on the result
	 * may cause the execution to be cancelled / aborted, but this is not guaranteed.
	 * 
	 * @param request safe to replay
	 * @param options options to apply to this request
	 * @param contextOpt - the optional context. The client should update this appropriately based
	 * on the received response before completing the result.
	 * @return a {@link CompletableFuture} to be completed with the response, or completed exceptionally
	 * otherwise, for example with an {@link java.io.IOException} on a network error connecting to {@link Request#url()}.
	 */
	CompletableFuture<Response> execute(Request request, Options options, Optional<C> contextOpt);

	class Default<C> implements AsyncClient<C> {
		
		private final Client client;
		private final ExecutorService executorService;
		
		public Default(Client client, ExecutorService executorService) {
			this.client = client;
			this.executorService = executorService;
		}

		@Override
		public CompletableFuture<Response> execute(Request request, Options options, Optional<C> contextOpt) {
			CompletableFuture<Response> result = new CompletableFuture<>();
			Future<?> future = executorService.submit(() -> {
				try {
					result.complete(client.execute(request, options));
				}catch(Exception e) {
					result.completeExceptionally(e);
				}
			});
			result.whenComplete( (response, throwable) -> {
				if (result.isCancelled())
					future.cancel(true);
			});
			return result;
		}
	}
	
}
