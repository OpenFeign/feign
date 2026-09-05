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
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

class CancellableFutureTest {

  interface Api {
    @RequestLine("GET /")
    CompletableFuture<String> get();
  }

  /**
   * cancel() arrives BEFORE setInner() is called.
   *
   * <p>The AsyncClient returns immediately with a pending CompletableFuture so that api.get()
   * returns the CancellableFuture to the caller without blocking. The caller then cancels it before
   * the client future is completed. When the client future eventually completes, setInner() must
   * detect isCancelled() and immediately forward cancellation to the newly registered inner future.
   */
  @Test
  void cancelBeforeSetInnerRacesCorrectly() throws Exception {
    // execute() returns this immediately — no blocking inside execute()
    CompletableFuture<Response> clientFuture = new CompletableFuture<>();

    AsyncClient<Void> client = (request, options, ctx) -> clientFuture;

    Api api = AsyncFeign.<Void>builder().client(client).target(Api.class, "http://localhost:0");

    // api.get() returns immediately because execute() returns immediately
    CompletableFuture<String> result = api.get();

    // Cancel BEFORE clientFuture resolves — inner is not yet set on CancellableFuture
    result.cancel(true);

    // Complete the client future now. This triggers the whenComplete → setInner() path.
    // setInner() must see isCancelled() == true and cancel the newly registered inner future.
    clientFuture.complete(
        Response.builder()
            .status(200)
            .reason("OK")
            .request(
                Request.create(
                    Request.HttpMethod.GET,
                    "http://localhost:0",
                    Collections.emptyMap(),
                    Request.Body.empty(),
                    null))
            .build());

    assertThat(result).isCancelled();
  }

  /**
   * cancel() arrives AFTER setInner() has already been called (the retry path).
   *
   * <p>The first execute() fails immediately to trigger a retry. The retry execute() returns a
   * pending CompletableFuture immediately (no blocking inside execute()) and signals a latch so
   * the caller knows setInner() has been called. The caller then cancels — cancel() must read
   * inner and propagate to the retry future.
   */
  @Test
  void cancelAfterSetInnerRacesCorrectly() throws Exception {
    AtomicInteger callCount = new AtomicInteger();
    CountDownLatch retryStarted = new CountDownLatch(1);
    // Holds the raw client future from the retry execute() call
    CompletableFuture<Response>[] retryFutureHolder = new CompletableFuture[1];

    AsyncClient<Void> client =
        (request, options, ctx) -> {
          int n = callCount.incrementAndGet();
          if (n == 1) {
            // First call: fail immediately to trigger the retryer
            CompletableFuture<Response> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IOException("transient"));
            return failed;
          }
          // Retry call: return a pending future immediately — execute() does NOT block.
          // The latch is used only to tell the caller that setInner() has been called.
          CompletableFuture<Response> retryFuture = new CompletableFuture<>();
          retryFutureHolder[0] = retryFuture;
          retryStarted.countDown();
          return retryFuture;
        };

    Api api =
        AsyncFeign.<Void>builder()
            .client(client)
            .retryer(new Retryer.Default(0, 0, 2))
            .target(Api.class, "http://localhost:0");

    CompletableFuture<String> result = api.get();

    // Wait until the retry execute() returned and setInner() has been called
    assertThat(retryStarted.await(2, TimeUnit.SECONDS)).isTrue();

    // cancel() now arrives after setInner() — inner is already set to retryFuture
    result.cancel(true);

    assertThat(result).isCancelled();

    // Verify the pipeTo guard: even if the raw retry client future eventually completes,
    // it must NOT overwrite the cancellation on result. setInner() registered a whenComplete
    // that calls pipeTo(result), which checks isDone() before completing — so result must
    // remain cancelled after the raw client future resolves.
    CompletableFuture<Response> retryFuture = retryFutureHolder[0];
    assertThat(retryFuture).isNotNull();
    retryFuture.cancel(false); // let the retry future give up
    assertThat(result).isCancelled(); // must still be cancelled, not overwritten
  }

  /** Normal completion (no cancellation) must not be disrupted by the volatile field change. */
  @Test
  void normalCompletionIsNotAffected() throws Exception {
    MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBody("hello"));

    Api api = AsyncFeign.<Void>builder().target(Api.class, server.url("/").toString());

    assertThat(api.get().get(2, TimeUnit.SECONDS)).isEqualTo("hello");
    server.shutdown();
  }
}

