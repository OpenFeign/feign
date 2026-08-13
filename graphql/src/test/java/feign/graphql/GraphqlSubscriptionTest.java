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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Feign;
import feign.jackson.JacksonCodec;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GraphqlSubscriptionTest {

  private final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private MockWebServer server;

  private final CountDownLatch closed = new CountDownLatch(1);
  private boolean expectsWebSocket;

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

    // blocks until the first event, then unsubscribes
    @GraphqlQuery(
        "subscription onPrice($symbol: String!) {"
            + " priceChanged(symbol: $symbol) { symbol price } }")
    Price firstPrice(String symbol);

    @GraphqlQuery(
        "subscription onPrice($symbol: String!) {"
            + " priceChanged(symbol: $symbol) { symbol price } }")
    Optional<Price> maybeFirstPrice(String symbol);

    @GraphqlQuery(
        "subscription onPrice($symbol: String!) {"
            + " priceChanged(symbol: $symbol) { symbol price } }")
    CompletableFuture<Price> futurePrice(String symbol);

    @GraphqlQuery(
        "subscription onPrice($symbol: String!) {"
            + " priceChanged(symbol: $symbol) { symbol price } }")
    void ignoredPrice(String symbol);

    // ordinary query on the same interface — goes over HTTP, not the web socket
    @GraphqlQuery(
        "query lastPrice($symbol: String!) { lastPrice(symbol: $symbol) { symbol price } }")
    Price lastPrice(String symbol);
  }

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
  }

  @AfterEach
  void tearDown() throws Exception {
    if (expectsWebSocket) {
      assertThat(closed.await(10, TimeUnit.SECONDS))
          .as("client should have closed the web socket")
          .isTrue();
    }
    server.shutdown();
  }

  private StockApi buildClient() {
    return Feign.builder()
        .addCapability(new GraphqlCapability(new JacksonCodec(mapper)))
        .target(StockApi.class, server.url("/graphql").toString());
  }

  /** {@code {id}} is replaced with the id the client actually subscribed with. */
  private static final String NEXT =
      "{\"id\":\"{id}\",\"type\":\"next\",\"payload\":{\"data\":{\"priceChanged\":"
          + "{\"symbol\":\"%s\",\"price\":%s}}}}";

  /** Replays the graphql-transport-ws handshake, then whatever the test queued. */
  private void enqueueServer(List<String> received, String... afterSubscribe) {
    expectsWebSocket = true;
    server.enqueue(
        new MockResponse()
            .withWebSocketUpgrade(
                new WebSocketListener() {
                  @Override
                  public void onMessage(WebSocket webSocket, String text) {
                    received.add(text);
                    try {
                      var message = mapper.readTree(text);
                      if ("connection_init".equals(message.get("type").asText())) {
                        webSocket.send("{\"type\":\"connection_ack\"}");
                      } else if ("subscribe".equals(message.get("type").asText())) {
                        var id = message.get("id").asText();
                        for (var queued : afterSubscribe) {
                          webSocket.send(queued.replace("{id}", id));
                        }
                      }
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
                }));
  }

  @Test
  void streamBlocksUntilEachEventArrives() throws Exception {
    var received = new CopyOnWriteArrayList<String>();
    enqueueServer(
        received,
        String.format(NEXT, "ACME", "10.5"),
        String.format(NEXT, "ACME", "11.25"),
        "{\"id\":\"{id}\",\"type\":\"complete\"}");

    List<Price> prices;
    try (var stream = buildClient().onPrice("ACME")) {
      prices = stream.collect(Collectors.toList());
    }

    assertThat(prices).extracting(price -> price.symbol).containsExactly("ACME", "ACME");
    assertThat(prices).extracting(price -> price.price).containsExactly(10.5, 11.25);

    assertThat(mapper.readTree(received.get(0)).get("type").asText()).isEqualTo("connection_init");

    var subscribe = mapper.readTree(received.get(1));
    assertThat(subscribe.get("type").asText()).isEqualTo("subscribe");
    assertThat(subscribe.get("payload").get("variables").get("symbol").asText()).isEqualTo("ACME");

    var id = subscribe.get("id").asText();
    assertThat(UUID.fromString(id)).hasToString(id);
  }

  @Test
  void publisherReturnsImmediatelyAndPushes() throws Exception {
    var received = new CopyOnWriteArrayList<String>();
    enqueueServer(
        received,
        String.format(NEXT, "ACME", "10.5"),
        String.format(NEXT, "ACME", "11.25"),
        "{\"id\":\"{id}\",\"type\":\"complete\"}");

    var publisher = buildClient().publishPrice("ACME");

    var delivered = new ArrayList<Price>();
    var completed = new CountDownLatch(1);
    publisher.subscribe(
        new Flow.Subscriber<Price>() {
          @Override
          public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
          }

          @Override
          public void onNext(Price item) {
            delivered.add(item);
          }

          @Override
          public void onError(Throwable throwable) {
            completed.countDown();
          }

          @Override
          public void onComplete() {
            completed.countDown();
          }
        });

    assertThat(completed.await(10, TimeUnit.SECONDS)).isTrue();
    assertThat(delivered).extracting(price -> price.price).containsExactly(10.5, 11.25);
  }

  @Test
  void serverErrorMessageFailsTheStream() {
    var received = new CopyOnWriteArrayList<String>();
    enqueueServer(
        received,
        "{\"id\":\"{id}\",\"type\":\"error\",\"payload\":[{\"message\":\"unknown symbol\"}]}");

    try (var stream = buildClient().onPrice("NOPE")) {
      assertThatThrownBy(stream::findFirst)
          .isInstanceOf(GraphqlErrorException.class)
          .hasMessageContaining("unknown symbol");
    }
  }

  @Test
  void errorsInsidePayloadFailTheStream() {
    var received = new CopyOnWriteArrayList<String>();
    enqueueServer(
        received,
        "{\"id\":\"{id}\",\"type\":\"next\",\"payload\":{\"errors\":[{\"message\":\"boom\"}]}}");

    try (var stream = buildClient().onPrice("ACME")) {
      assertThatThrownBy(stream::findFirst)
          .isInstanceOf(GraphqlErrorException.class)
          .hasMessageContaining("boom");
    }
  }

  @Test
  void plainReturnTypeBlocksForTheFirstEventOnly() {
    var received = new CopyOnWriteArrayList<String>();
    enqueueServer(received, String.format(NEXT, "ACME", "10.5"), String.format(NEXT, "ACME", "99"));

    var price = buildClient().firstPrice("ACME");

    assertThat(price.price).isEqualTo(10.5);
  }

  @Test
  void optionalReturnTypeBlocksForTheFirstEvent() {
    var received = new CopyOnWriteArrayList<String>();
    enqueueServer(received, String.format(NEXT, "ACME", "10.5"));

    assertThat(buildClient().maybeFirstPrice("ACME"))
        .hasValueSatisfying(price -> assertThat(price.price).isEqualTo(10.5));
  }

  @Test
  void optionalReturnTypeIsEmptyWhenTheServerCompletesWithoutEvents() {
    var received = new CopyOnWriteArrayList<String>();
    enqueueServer(received, "{\"id\":\"{id}\",\"type\":\"complete\"}");

    assertThat(buildClient().maybeFirstPrice("ACME")).isEmpty();
  }

  @Test
  void futureReturnsImmediatelyAndCompletesWithTheFirstEvent() throws Exception {
    var received = new CopyOnWriteArrayList<String>();
    enqueueServer(received, String.format(NEXT, "ACME", "10.5"));

    var future = buildClient().futurePrice("ACME");

    assertThat(future.get(10, TimeUnit.SECONDS).price).isEqualTo(10.5);
  }

  @Test
  void voidReturnTypeClosesTheSubscription() {
    var received = new CopyOnWriteArrayList<String>();
    enqueueServer(received, String.format(NEXT, "ACME", "10.5"));

    buildClient().ignoredPrice("ACME");
    // tearDown asserts the socket was closed rather than leaked
  }

  @Test
  void eventTimeoutBoundsBlockingCalls() {
    var received = new CopyOnWriteArrayList<String>();
    enqueueServer(received);

    var api =
        Feign.builder()
            .addCapability(new GraphqlCapability(new JacksonCodec(mapper), Duration.ofMillis(250)))
            .target(StockApi.class, server.url("/graphql").toString());

    assertThatThrownBy(() -> api.firstPrice("ACME"))
        .rootCause()
        .isInstanceOf(SocketTimeoutException.class);
  }

  @Test
  void eventTimeoutAlsoBoundsStreamElements() {
    var received = new CopyOnWriteArrayList<String>();
    enqueueServer(received, String.format(NEXT, "ACME", "10.5"));

    var api =
        Feign.builder()
            .addCapability(new GraphqlCapability(new JacksonCodec(mapper), Duration.ofMillis(250)))
            .target(StockApi.class, server.url("/graphql").toString());

    try (var stream = api.onPrice("ACME")) {
      // the server never completes, so the second element hits the timeout
      assertThatThrownBy(stream::toList).rootCause().isInstanceOf(SocketTimeoutException.class);
    }
  }

  @Test
  void asyncFormsAreNotBoundedByTheEventTimeout() throws Exception {
    var received = new CopyOnWriteArrayList<String>();
    enqueueServer(received);

    var api =
        Feign.builder()
            .addCapability(new GraphqlCapability(new JacksonCodec(mapper), Duration.ofMillis(100)))
            .target(StockApi.class, server.url("/graphql").toString());

    var future = api.futurePrice("ACME");

    assertThatThrownBy(() -> future.get(500, TimeUnit.MILLISECONDS))
        .isInstanceOf(TimeoutException.class);
    assertThat(future).isNotCompleted();

    // deliberately still waiting on the server, so there is no close to assert on
    expectsWebSocket = false;
  }

  @Test
  void queriesAndSubscriptionsShareOneClient() throws Exception {
    server.enqueue(
        new MockResponse()
            .setBody("{\"data\":{\"lastPrice\":{\"symbol\":\"ACME\",\"price\":9.75}}}")
            .addHeader("Content-Type", "application/json"));
    var received = new CopyOnWriteArrayList<String>();
    enqueueServer(
        received, String.format(NEXT, "ACME", "10.5"), "{\"id\":\"{id}\",\"type\":\"complete\"}");

    var api = buildClient();

    assertThat(api.lastPrice("ACME").price).isEqualTo(9.75);
    try (var stream = api.onPrice("ACME")) {
      assertThat(stream.toList()).extracting(price -> price.price).containsExactly(10.5);
    }

    var query = server.takeRequest();
    assertThat(query.getMethod()).isEqualTo("POST");
    assertThat(query.getHeader("Upgrade")).isNull();

    var handshake = server.takeRequest();
    assertThat(handshake.getHeader("Upgrade")).isEqualToIgnoringCase("websocket");
    assertThat(handshake.getHeader("Sec-WebSocket-Protocol")).contains("graphql-transport-ws");
  }

  @Test
  void eventsForAnotherOperationAreIgnored() {
    var received = new CopyOnWriteArrayList<String>();
    enqueueServer(
        received,
        "{\"id\":\"someone-else\",\"type\":\"next\",\"payload\":{\"data\":{\"priceChanged\":"
            + "{\"symbol\":\"NOPE\",\"price\":1}}}}",
        String.format(NEXT, "ACME", "10.5"),
        "{\"id\":\"{id}\",\"type\":\"complete\"}");

    try (var stream = buildClient().onPrice("ACME")) {
      assertThat(stream.toList()).extracting(price -> price.symbol).containsExactly("ACME");
    }
  }

  @Test
  void slowConsumerStillReceivesEveryEvent() {
    var received = new CopyOnWriteArrayList<String>();
    enqueueServer(
        received,
        String.format(NEXT, "ACME", "1"),
        String.format(NEXT, "ACME", "2"),
        String.format(NEXT, "ACME", "3"),
        String.format(NEXT, "ACME", "4"),
        String.format(NEXT, "ACME", "5"),
        "{\"id\":\"{id}\",\"type\":\"complete\"}");

    // Reads are demand-driven, so a consumer that lags must still be handed every event in order.
    // Broken demand accounting stalls here until the event timeout instead.
    List<Price> prices;
    try (var stream = buildClient().onPrice("ACME")) {
      prices =
          stream
              .peek(
                  price -> {
                    try {
                      Thread.sleep(20);
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                    }
                  })
              .toList();
    }

    assertThat(prices).extracting(price -> price.price).containsExactly(1.0, 2.0, 3.0, 4.0, 5.0);
  }

  @Test
  void cancellingTheFutureClosesTheSubscription() {
    var received = new CopyOnWriteArrayList<String>();
    enqueueServer(received);

    var future = buildClient().futurePrice("ACME");
    assertThat(future.cancel(true)).isTrue();

    // tearDown asserts the web socket was closed rather than left hanging on a cancelled future
  }

  @Test
  void defaultEventTimeoutIsOneMinute() {
    assertThat(GraphqlDecoder.DEFAULT_EVENT_TIMEOUT).isEqualTo(Duration.ofMinutes(1));
  }

  @Test
  void subscriptionDetection() {
    assertThat(GraphqlContract.isSubscription("subscription onPrice { a }")).isTrue();
    assertThat(GraphqlContract.isSubscription("  \n subscription { a }")).isTrue();
    assertThat(GraphqlContract.isSubscription("query subscriptionLike { a }")).isFalse();
    assertThat(GraphqlContract.isSubscription("mutation m { a }")).isFalse();
  }

  @Test
  void webSocketUriSwapsScheme() {
    assertThat(GraphqlSubscriptionClient.webSocketUri("http://host:8080/graphql"))
        .hasToString("ws://host:8080/graphql");
    assertThat(GraphqlSubscriptionClient.webSocketUri("https://host/graphql"))
        .hasToString("wss://host/graphql");
  }
}
