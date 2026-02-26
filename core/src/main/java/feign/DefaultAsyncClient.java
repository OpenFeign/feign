/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign;

import feign.Request.Options;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Experimental
public class DefaultAsyncClient<C> implements AsyncClient<C> {

  private final Client client;
  private final ExecutorService executorService;

  public DefaultAsyncClient(Client client, ExecutorService executorService) {
    this.client = client;
    this.executorService = executorService;
  }

  @Override
  public CompletableFuture<Response> execute(
      Request request, Options options, Optional<C> requestContext) {
    final CompletableFuture<Response> result = new CompletableFuture<>();
    final Future<?> future =
        executorService.submit(
            () -> {
              try {
                result.complete(client.execute(request, options));
              } catch (final Exception e) {
                result.completeExceptionally(e);
              }
            });
    result.whenComplete(
        (response, throwable) -> {
          if (result.isCancelled()) {
            future.cancel(true);
          }
        });
    return result;
  }
}
