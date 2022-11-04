/*
 * Copyright 2012-2022 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign;

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

/**
 * Zero or One {@code ClientInterceptor} may be configured for purposes such as tracing - mutate
 * headers, execute the call, parse the response and close any previously created objects.
 */
public interface ClientInterceptor {

  /**
   * Returns the default instance of {@link ClientInterceptor}. We don't need to check if the
   * iterator has a next entry because the {@link SynchronousMethodHandler} will add 1 entry. That
   * means that in the default scenario an iterator to that entry will be passed here.
   */
  ClientInterceptor DEFAULT = (context, iterator) -> {
    ClientInterceptor next = iterator.next();
    return next.around(context, iterator);
  };

  /**
   * Allows to make an around instrumentation. Remember to call the next interceptor by calling
   * {@code ClientInterceptor interceptor = iterator.next(); return interceptor.around(context, iterator)}.
   *
   * @param context input context to send a request
   * @param iterator iterator to the next {@link ClientInterceptor}
   * @return response or an exception - remember to rethrow an exception if it occurrs
   * @throws FeignException exception while trying to send a request
   */
  WrappedResponse around(ClientInvocationContext context, Iterator<ClientInterceptor> iterator)
      throws FeignException;

  /**
   * Wrapper around a response. Used in both synchronous and asynchronous communication. For
   * synchronous the unwrapped object must be of type {@link Response}. For asynchronous the
   * unwrapped object must be of type {@code CompletableFuture<Response>}
   */
  interface WrappedResponse {
    /**
     * Unwraps the actual response type for sync communication.
     * 
     * @return the actual response type for sync
     */
    Response unwrapSync();

    /**
     * Unwraps the actual response type for async communications.
     * 
     * @return the actual response type for async
     */
    CompletableFuture<Response> unwrapAsync();

    /**
     * Returns {@code true} for async communication.
     * 
     * @return {@code true} for async communication
     */
    boolean isAsync();

    /**
     * Returns {@code true} for sync communication.
     * 
     * @return {@code true} for sync communication
     */
    default boolean isSync() {
      return !isAsync();
    }

  }

  /**
   * {@link WrappedResponse} for async communication.
   */
  class AsyncResponse implements ClientInterceptor.WrappedResponse {
    private final CompletableFuture<Response> response;

    public AsyncResponse(CompletableFuture<Response> response) {
      this.response = response;
    }

    @Override
    public Response unwrapSync() {
      throw new UnsupportedOperationException("AsyncResponse must be used only in async scope");
    }

    @Override
    public CompletableFuture<Response> unwrapAsync() {
      return this.response;
    }

    @Override
    public boolean isAsync() {
      return true;
    }
  }

  /**
   * {@link WrappedResponse} for sync communication.
   */
  class SyncResponse implements ClientInterceptor.WrappedResponse {
    private final Response response;

    public SyncResponse(Response response) {
      this.response = response;
    }

    @Override
    public Response unwrapSync() {
      return this.response;
    }

    @Override
    public CompletableFuture<Response> unwrapAsync() {
      throw new UnsupportedOperationException("SyncResponse must be used only in sync scope");
    }

    @Override
    public boolean isAsync() {
      return false;
    }
  }
}
