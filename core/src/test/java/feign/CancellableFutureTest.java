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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

class CancellableFutureTest {

  interface Api {
    @RequestLine("GET /")
    CompletableFuture<String> get();
  }

  private final List<CompletableFuture<Response>> clientCalls = new CopyOnWriteArrayList<>();

  private final AsyncClient<Void> pendingClient =
      (request, options, requestContext) -> {
        CompletableFuture<Response> call = new CompletableFuture<>();
        clientCalls.add(call);
        return call;
      };

  @Test
  void cancelDuringRetryDecisionStopsTheRetryChain() throws Exception {
    CountDownLatch retryDecisionStarted = new CountDownLatch(1);
    CountDownLatch outerCancelled = new CountDownLatch(1);
    Retryer pausingOnFirstRetry =
        new AlwaysRetry() {
          private boolean paused;

          @Override
          public void continueOrPropagate(RetryableException e) {
            if (!paused) {
              paused = true;
              retryDecisionStarted.countDown();
              awaitUninterruptibly(outerCancelled);
            }
          }
        };
    Api api =
        AsyncFeign.<Void>builder()
            .client(pendingClient)
            .retryer(pausingOnFirstRetry)
            .target(Api.class, "http://localhost:0");

    CompletableFuture<String> result = api.get();
    Thread firstCallFailure =
        new Thread(() -> clientCalls.get(0).completeExceptionally(new IOException("first")));
    firstCallFailure.start();
    assertThat(retryDecisionStarted.await(5, TimeUnit.SECONDS)).isTrue();

    result.cancel(true);
    outerCancelled.countDown();
    firstCallFailure.join(TimeUnit.SECONDS.toMillis(5));
    clientCalls.get(1).completeExceptionally(new IOException("second"));

    assertThat(result).isCancelled();
    assertThat(clientCalls).hasSize(2);
  }

  @Test
  void cancelAfterRetryStartedStopsTheRetryChain() {
    Api api =
        AsyncFeign.<Void>builder()
            .client(pendingClient)
            .retryer(new AlwaysRetry())
            .target(Api.class, "http://localhost:0");

    CompletableFuture<String> result = api.get();
    clientCalls.get(0).completeExceptionally(new IOException("first"));
    assertThat(clientCalls).hasSize(2);

    result.cancel(true);
    clientCalls.get(1).completeExceptionally(new IOException("second"));

    assertThat(result).isCancelled();
    assertThat(clientCalls).hasSize(2);
  }

  @Test
  void normalCompletionIsNotAffected() throws Exception {
    MockWebServer server = new MockWebServer();
    try {
      server.enqueue(new MockResponse().setBody("hello"));

      Api api = AsyncFeign.<Void>builder().target(Api.class, server.url("/").toString());

      assertThat(api.get().get(2, TimeUnit.SECONDS)).isEqualTo("hello");
    } finally {
      server.shutdown();
    }
  }

  private static void awaitUninterruptibly(CountDownLatch latch) {
    try {
      latch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static class AlwaysRetry implements Retryer {

    @Override
    public void continueOrPropagate(RetryableException e) {}

    @Override
    public Retryer clone() {
      return this;
    }
  }
}
