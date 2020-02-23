/**
 * Copyright 2012-2020 The Feign Authors
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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import feign.Request.Options;

/**
 * Submits HTTP {@link Request requests} asynchronously, with an optional context.
 */
@Experimental
public interface AsyncClient<C> {

  /**
   * Executes the request asynchronously. Calling {@link CompletableFuture#cancel(boolean)} on the
   * result may cause the execution to be cancelled / aborted, but this is not guaranteed.
   *
   * @param request safe to replay
   * @param options options to apply to this request
   * @param requestContext - the optional context, for example for storing session cookies. The
   *        client should update this appropriately based on the received response before completing
   *        the result.
   * @return a {@link CompletableFuture} to be completed with the response, or completed
   *         exceptionally otherwise, for example with an {@link java.io.IOException} on a network
   *         error connecting to {@link Request#url()}.
   */
  CompletableFuture<Response> execute(Request request, Options options, Optional<C> requestContext);

  class Default<C> implements AsyncClient<C> {

    private final Client client;
    private final ExecutorService executorService;

    public Default(Client client, ExecutorService executorService) {
      this.client = client;
      this.executorService = executorService;
    }

    @Override
    public CompletableFuture<Response> execute(Request request,
                                               Options options,
                                               Optional<C> requestContext) {
      final CompletableFuture<Response> result = new CompletableFuture<>();
      final Future<?> future = executorService.submit(() -> {
        try {
          result.complete(client.execute(request, options));
        } catch (final Exception e) {
          result.completeExceptionally(e);
        }
      });
      result.whenComplete((response, throwable) -> {
        if (result.isCancelled()) {
          future.cancel(true);
        }
      });
      return result;
    }
  }

  /**
   * A synchronous implementation of {@link AsyncClient}
   *
   * @param <C> - unused context; synchronous clients handle context internally
   */
  class Pseudo<C> implements AsyncClient<C> {

    private final Client client;

    public Pseudo(Client client) {
      this.client = client;
    }

    @Override
    public CompletableFuture<Response> execute(Request request,
                                               Options options,
                                               Optional<C> requestContext) {
      final CompletableFuture<Response> result = new CompletableFuture<>();
      try {
        result.complete(client.execute(request, options));
      } catch (final Exception e) {
        result.completeExceptionally(e);
      }

      return result;
    }
  }
}
