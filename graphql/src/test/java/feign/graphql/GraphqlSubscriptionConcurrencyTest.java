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
package feign.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Feign;
import feign.jackson.JacksonCodec;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the subscription wiring under concurrent load: many sockets open at once, sharing one
 * capability, one JSON codec and one worker pool.
 */
class GraphqlSubscriptionConcurrencyTest {

  private static final int SUBSCRIPTIONS = 24;
  private static final int EVENTS_EACH = 20;

  private final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private MockWebServer server;

  /** Counts sockets the client closed, so leaks show up as a shortfall. */
  private final CountDownLatch closed = new CountDownLatch(SUBSCRIPTIONS);

  private final AtomicInteger openSockets = new AtomicInteger();

  /**
   * When false the server acknowledges the subscribe and then stays quiet, as a real feed would.
   */
  private volatile boolean emitEvents = true;

  public static class Price {
    public String symbol;
    public double price;
  }

  interface StockApi {

    @GraphqlQuery(
        "subscription onPrice($symbol: String!) {"
            + " priceChanged(symbol: $symbol) { symbol price } }")
    Stream<Price> onPrice(String symbol);

    @GraphqlQuery(
        "subscription onPrice($symbol: String!) {"
            + " priceChanged(symbol: $symbol) { symbol price } }")
    Flow.Publisher<Price> publishPrice(String symbol);

    @GraphqlQuery(
        "subscription onPrice($symbol: String!) {"
            + " priceChanged(symbol: $symbol) { symbol price } }")
    CompletableFuture<Price> futurePrice(String symbol);
  }

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    // A dispatcher rather than a queue: every connection gets its own upgrade and its own listener,
    // so the subscriptions are genuinely independent sockets.
    server.setDispatcher(
        new Dispatcher() {
          @Override
          public MockResponse dispatch(RecordedRequest request) {
            return new MockResponse().withWebSocketUpgrade(new EchoingServer());
          }
        });
    server.start();
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  /** Replays the handshake, then emits the requested symbol back with the client's own id. */
  private final class EchoingServer extends WebSocketListener {

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
      openSockets.incrementAndGet();
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
      try {
        var message = mapper.readTree(text);
        var type = message.get("type").asText();
        if ("connection_init".equals(type)) {
          webSocket.send("{\"type\":\"connection_ack\"}");
          return;
        }
        if (!"subscribe".equals(type)) {
          return;
        }
        if (!emitEvents) {
          return;
        }
        var id = message.get("id").asText();
        var symbol = message.get("payload").get("variables").get("symbol").asText();
        for (var i = 0; i < EVENTS_EACH; i++) {
          webSocket.send(
              "{\"id\":\""
                  + id
                  + "\",\"type\":\"next\",\"payload\":{\"data\":{\"priceChanged\":{\"symbol\":\""
                  + symbol
                  + "\",\"price\":"
                  + i
                  + "}}}}");
        }
        webSocket.send("{\"id\":\"" + id + "\",\"type\":\"complete\"}");
      } catch (Exception e) {
        throw new IllegalStateException("bad client message: " + text, e);
      }
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
      webSocket.close(code, reason);
      closed.countDown();
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
      closed.countDown();
    }
  }

  private StockApi buildClient() {
    return Feign.builder()
        .addCapability(new GraphqlCapability(new JacksonCodec(mapper), Duration.ofSeconds(30)))
        .target(StockApi.class, server.url("/graphql").toString());
  }

  private StockApi buildClient(Executor executor) {
    return Feign.builder()
        .addCapability(
            new GraphqlCapability(new JacksonCodec(mapper), Duration.ofSeconds(30), executor))
        .target(StockApi.class, server.url("/graphql").toString());
  }

  private int drain(Flow.Publisher<Price> publisher) throws Exception {
    var delivered = new AtomicInteger();
    var done = new CountDownLatch(1);
    publisher.subscribe(
        new Flow.Subscriber<Price>() {
          @Override
          public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
          }

          @Override
          public void onNext(Price item) {
            delivered.incrementAndGet();
          }

          @Override
          public void onError(Throwable throwable) {
            done.countDown();
          }

          @Override
          public void onComplete() {
            done.countDown();
          }
        });
    assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
    return delivered.get();
  }

  private <T> List<T> runAllAtOnce(List<Callable<T>> tasks) throws Exception {
    var pool = Executors.newFixedThreadPool(tasks.size());
    try {
      var barrier = new CyclicBarrier(tasks.size());
      var futures =
          tasks.stream()
              .map(
                  task ->
                      pool.submit(
                          () -> {
                            barrier.await(30, TimeUnit.SECONDS);
                            return task.call();
                          }))
              .toList();
      var results = new java.util.ArrayList<T>();
      for (var future : futures) {
        results.add(future.get(60, TimeUnit.SECONDS));
      }
      return results;
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void concurrentStreamsStayIsolated() throws Exception {
    var api = buildClient();

    List<Callable<List<Price>>> tasks =
        IntStream.range(0, SUBSCRIPTIONS)
            .<Callable<List<Price>>>mapToObj(
                index ->
                    () -> {
                      try (var prices = api.onPrice("SYM" + index)) {
                        return prices.toList();
                      }
                    })
            .toList();

    var results = runAllAtOnce(tasks);

    // Every subscription sees exactly its own events, in order, with nothing from its neighbours.
    for (var index = 0; index < SUBSCRIPTIONS; index++) {
      var prices = results.get(index);
      assertThat(prices).hasSize(EVENTS_EACH);
      assertThat(prices).extracting(price -> price.symbol).containsOnly("SYM" + index);
      assertThat(prices)
          .extracting(price -> price.price)
          .containsExactlyElementsOf(
              IntStream.range(0, EVENTS_EACH).mapToObj(i -> (double) i).toList());
    }

    assertThat(openSockets).hasValue(SUBSCRIPTIONS);
    assertThat(closed.await(30, TimeUnit.SECONDS))
        .as("every socket should have been closed, not leaked")
        .isTrue();
  }

  @Test
  void concurrentPublishersDeliverEveryEvent() throws Exception {
    var api = buildClient();

    List<Callable<Integer>> tasks =
        IntStream.range(0, SUBSCRIPTIONS)
            .<Callable<Integer>>mapToObj(
                index ->
                    () -> {
                      var delivered = new AtomicInteger();
                      var done = new CountDownLatch(1);
                      api.publishPrice("SYM" + index)
                          .subscribe(
                              new Flow.Subscriber<Price>() {
                                @Override
                                public void onSubscribe(Flow.Subscription subscription) {
                                  subscription.request(Long.MAX_VALUE);
                                }

                                @Override
                                public void onNext(Price item) {
                                  delivered.incrementAndGet();
                                }

                                @Override
                                public void onError(Throwable throwable) {
                                  done.countDown();
                                }

                                @Override
                                public void onComplete() {
                                  done.countDown();
                                }
                              });
                      assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
                      return delivered.get();
                    })
            .toList();

    assertThat(runAllAtOnce(tasks)).containsOnly(EVENTS_EACH);
    assertThat(closed.await(30, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void aPoolSizedForTheSubscriptionsIsEnough() throws Exception {
    // One worker per open subscription is the documented cost. Needing a second thread per
    // subscription for delivery would starve this pool and hang instead.
    var pool = Executors.newFixedThreadPool(SUBSCRIPTIONS);
    try {
      var api = buildClient(pool);
      List<Callable<Integer>> tasks =
          IntStream.range(0, SUBSCRIPTIONS)
              .<Callable<Integer>>mapToObj(index -> () -> drain(api.publishPrice("SYM" + index)))
              .toList();

      assertThat(runAllAtOnce(tasks)).containsOnly(EVENTS_EACH);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void aPoolTooSmallRefusesRatherThanStranding() throws Exception {
    var pool = new ThreadPoolExecutor(0, 4, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());
    try {
      var api = buildClient(pool);
      List<Callable<Integer>> tasks =
          IntStream.range(0, SUBSCRIPTIONS)
              .<Callable<Integer>>mapToObj(index -> () -> drain(api.publishPrice("SYM" + index)))
              .toList();

      // Far more subscriptions than workers. Some are refused, but every subscriber must reach a
      // terminal signal — drain() asserts that. Leaving one waiting forever is the failure mode.
      assertThat(runAllAtOnce(tasks)).hasSize(SUBSCRIPTIONS);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void longLivedSubscriptionsAreNotCappedByCoreCount() throws Exception {
    emitEvents = false;
    var api = buildClient();

    // Each of these holds its worker parked on the queue for as long as it is open, which is what a
    // real feed does. A pool sized from the core count would refuse the excess synchronously.
    var futures =
        IntStream.range(0, SUBSCRIPTIONS)
            .mapToObj(index -> api.futurePrice("SYM" + index))
            .toList();

    assertThat(futures)
        .as("no subscription should have been refused a worker")
        .allSatisfy(future -> assertThat(future).isNotCompleted());

    futures.forEach(future -> future.cancel(true));
    assertThat(closed.await(30, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void closingMidStreamFromAnotherThreadTerminatesPromptly() throws Exception {
    var api = buildClient();

    List<Callable<Integer>> tasks =
        IntStream.range(0, SUBSCRIPTIONS)
            .<Callable<Integer>>mapToObj(
                index ->
                    () -> {
                      // Take a couple of events and walk away while the server is still pushing.
                      try (var prices = api.onPrice("SYM" + index)) {
                        return prices.limit(2).toList().size();
                      }
                    })
            .toList();

    assertThat(runAllAtOnce(tasks)).containsOnly(2);
    assertThat(closed.await(30, TimeUnit.SECONDS))
        .as("abandoning a stream must still close its socket")
        .isTrue();
  }
}
