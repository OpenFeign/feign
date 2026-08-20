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

import static java.nio.charset.StandardCharsets.UTF_8;

import feign.Client;
import feign.Experimental;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import feign.Util;
import feign.codec.JsonDecoder;
import feign.codec.JsonEncoder;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Executes {@code subscription} operations over the <a
 * href="https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md">graphql-transport-ws</a>
 * WebSocket protocol, delegating every other request to the wrapped {@link Client}.
 *
 * <p>The endpoint is the target URL with its scheme swapped to {@code ws}/{@code wss}. One
 * WebSocket connection is opened per subscription call and is closed when the returned {@code
 * Stream} or {@code Flow.Publisher} is closed/cancelled.
 */
@Experimental
public class GraphqlSubscriptionClient implements Client {

  private final Client delegate;
  private final GraphqlContract contract;
  private final JsonEncoder jsonEncoder;
  private final JsonDecoder jsonDecoder;
  private final HttpClient httpClient;

  public GraphqlSubscriptionClient(
      Client delegate, GraphqlContract contract, JsonEncoder encoder, JsonDecoder decoder) {
    this(delegate, contract, encoder, decoder, HttpClient.newHttpClient());
  }

  public GraphqlSubscriptionClient(
      Client delegate,
      GraphqlContract contract,
      JsonEncoder encoder,
      JsonDecoder decoder,
      HttpClient httpClient) {
    this.delegate = delegate;
    this.contract = contract;
    this.jsonEncoder = encoder;
    this.jsonDecoder = decoder;
    this.httpClient = httpClient;
  }

  @Override
  public Response execute(Request request, Request.Options options) throws IOException {
    var meta =
        request.requestTemplate() == null
            ? null
            : contract.lookupMetadata(request.requestTemplate());
    if (meta == null || !meta.subscription) {
      return delegate.execute(request, options);
    }
    return subscribe(request, options, meta);
  }

  private Response subscribe(
      Request request, Request.Options options, GraphqlContract.QueryMetadata meta)
      throws IOException {
    var subscription = new Subscription(request, meta, jsonEncoder, jsonDecoder);

    var builder = httpClient.newWebSocketBuilder().subprotocols("graphql-transport-ws");
    if (options != null && options.connectTimeoutMillis() > 0) {
      builder.connectTimeout(Duration.ofMillis(options.connectTimeoutMillis()));
    }
    request
        .headers()
        .forEach(
            (name, values) -> {
              if (isForwardable(name)) {
                values.forEach(value -> builder.header(name, value));
              }
            });

    try {
      subscription.attach(builder.buildAsync(webSocketUri(request.url()), subscription).join());
    } catch (CompletionException e) {
      var cause = e.getCause() == null ? e : e.getCause();
      throw new IOException("failed to open GraphQL subscription to " + request.url(), cause);
    }

    // 204 keeps feign's logger from draining and replacing the body, which would drop the live
    // subscription. Nothing here ever crosses the wire.
    return Response.builder()
        .status(HttpURLConnection.HTTP_NO_CONTENT)
        .reason("Subscribed")
        .request(request)
        .headers(Collections.emptyMap())
        .body(subscription)
        .build();
  }

  /** Headers the JDK WebSocket handshake rejects or manages itself. */
  private static boolean isForwardable(String header) {
    var name = header.toLowerCase(Locale.ROOT);
    return !name.equals("connection")
        && !name.equals("upgrade")
        && !name.equals("host")
        && !name.equals("content-type")
        && !name.equalsIgnoreCase(Util.CONTENT_LENGTH)
        && !name.startsWith("sec-websocket-");
  }

  static URI webSocketUri(String url) {
    var uri = URI.create(url);
    var scheme = "https".equalsIgnoreCase(uri.getScheme()) ? "wss" : "ws";
    return URI.create(scheme + url.substring(url.indexOf(':')));
  }

  /**
   * The messages this client sends, one record per <em>wire shape</em> rather than per type: the
   * configured {@link JsonEncoder} writes every component, so a shape carrying a component the
   * protocol does not define for that message would send it as null.
   */
  sealed interface ClientMessage {

    /** A bare type, covering {@code connection_init} and {@code pong}. */
    record Control(String type) implements ClientMessage {}

    /** A reference to a running operation. */
    record Complete(String id, String type) implements ClientMessage {}

    /** A reference to an operation plus the request that starts it. */
    record Subscribe(String id, String type, Operation payload) implements ClientMessage {}
  }

  /** The GraphQL request a subscription starts, as feign already encoded it into the body. */
  record Operation(String query, Map<String, Object> variables) {}

  /**
   * The envelope of a server message. Carries every component graphql-transport-ws defines, so a
   * strict mapper has nothing unknown to reject.
   *
   * @param payload stays untyped: {@code next} carries a {@code {data, errors}} object while {@code
   *     error} carries a list of errors.
   */
  record ServerMessage(String id, String type, Object payload) {

    @SuppressWarnings("unchecked")
    Map<String, Object> payloadFields() {
      return payload instanceof Map<?, ?> fields ? (Map<String, Object>) fields : Map.of();
    }
  }

  /**
   * A live subscription: the WebSocket listener, the queue of decoded {@code next} payloads and the
   * {@link Response.Body} handed to {@link GraphqlDecoder} all in one, because they share a
   * lifecycle.
   */
  static final class Subscription implements WebSocket.Listener, Response.Body {

    private static final Object DONE = new Object();

    /**
     * Unique per connection, so a stray message for another operation is never mistaken for ours.
     */
    private final String operationId = UUID.randomUUID().toString();

    /** Bounded: demand-driven reads keep this near empty, the capacity is a safety net. */
    private final BlockingQueue<Object> events = new LinkedBlockingQueue<>(1024);

    private final StringBuilder partial = new StringBuilder();
    private final Request request;
    private final GraphqlContract.QueryMetadata meta;
    private final JsonEncoder jsonEncoder;
    private final JsonDecoder jsonDecoder;

    /**
     * The already-encoded request body, decoded back so it can be sent as the subscribe payload.
     */
    private final Operation operation;

    private final AtomicBoolean detached = new AtomicBoolean();
    private final AtomicBoolean unsubscribed = new AtomicBoolean();

    private volatile WebSocket webSocket;
    private CompletableFuture<?> sends = CompletableFuture.completedFuture(null);

    Subscription(
        Request request,
        GraphqlContract.QueryMetadata meta,
        JsonEncoder jsonEncoder,
        JsonDecoder jsonDecoder)
        throws IOException {
      this.request = request;
      this.meta = meta;
      this.jsonEncoder = jsonEncoder;
      this.jsonDecoder = jsonDecoder;

      this.operation =
          request.body().isEmpty()
              ? new Operation(meta.query, Map.of())
              : decode(request.body().get().writeToString(UTF_8), Operation.class);
    }

    void attach(WebSocket webSocket) {
      this.webSocket = webSocket;
    }

    /**
     * Hands the subscription lifecycle to the decoder, so feign closing the response body right
     * after decoding no longer tears it down.
     */
    void detach() {
      detached.set(true);
    }

    Request request() {
      return request;
    }

    /**
     * Blocking stream of raw {@code {data, errors}} payloads, one per {@code next} message.
     *
     * @param timeoutMillis how long to wait for each event; {@code 0} waits indefinitely
     */
    Stream<Map<String, Object>> payloads(long timeoutMillis) {
      return StreamSupport.stream(
              Spliterators.spliteratorUnknownSize(
                  new PayloadIterator(timeoutMillis), Spliterator.ORDERED),
              false)
          .onClose(this::unsubscribe);
    }

    @Override
    public void onOpen(WebSocket ws) {
      send(ws, new ClientMessage.Control("connection_init"));
      ws.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
      partial.append(data);
      if (!last) {
        ws.request(1);
        return null;
      }
      var text = partial.toString();
      partial.setLength(0);
      // Only control frames pull the next one eagerly. A queued payload waits for the consumer to
      // take it, which is what bounds the queue.
      if (!handle(ws, text)) {
        ws.request(1);
      }
      return null;
    }

    @Override
    public void onError(WebSocket ws, Throwable error) {
      publish(error);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
      publish(DONE);
      return null;
    }

    /**
     * @return true when this message queued an event, so the next frame waits for the consumer.
     */
    private boolean handle(WebSocket ws, String text) {
      ServerMessage message;
      try {
        message = decode(text, ServerMessage.class);
      } catch (IOException | RuntimeException e) {
        return publish(e);
      }
      if (message == null || (message.id() != null && !message.id().equals(operationId))) {
        return false;
      }

      return switch (message.type()) {
        case "connection_ack" -> {
          send(ws, new ClientMessage.Subscribe(operationId, "subscribe", operation));
          yield false;
        }
        case "next" -> publish(message.payloadFields());
        case "error" ->
            publish(
                new GraphqlErrorException(
                    HttpURLConnection.HTTP_OK,
                    GraphqlContract.extractOperationField(meta.query),
                    String.valueOf(message.payload()),
                    request));
        case "complete" -> publish(DONE);
        case "ping" -> {
          send(ws, new ClientMessage.Control("pong"));
          yield false;
        }
        default -> false;
      };
    }

    private boolean publish(Object event) {
      if (!events.offer(event)) {
        // Unreachable while reads are demand-driven; failing loudly beats growing without bound.
        events.clear();
        events.offer(new IllegalStateException("GraphQL subscription event queue overflowed"));
      }
      return true;
    }

    private <T> T decode(String json, Class<T> type) throws IOException {
      var envelope =
          Response.builder()
              .status(HttpURLConnection.HTTP_OK)
              .headers(Collections.emptyMap())
              .request(request)
              .body(json, UTF_8)
              .build();
      return type.cast(jsonDecoder.decode(envelope, type));
    }

    /**
     * Sends are serialized: the JDK rejects a send while another is still in flight. A failure is
     * surfaced to the consumer and the chain reset, so it cannot silently swallow later sends.
     */
    private synchronized void send(WebSocket ws, ClientMessage message) {
      var json = toJson(message);
      sends =
          sends
              .thenCompose(ignored -> ws.sendText(json, true))
              .handle(
                  (ignored, error) -> {
                    if (error != null) {
                      publish(error);
                    }
                    return null;
                  });
    }

    private String toJson(ClientMessage message) {
      var template = new RequestTemplate();
      jsonEncoder.encode(message, message.getClass(), template);
      try {
        return template.requestBody().get().writeToString(UTF_8);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    void unsubscribe() {
      if (!unsubscribed.compareAndSet(false, true)) {
        return;
      }
      var ws = webSocket;
      publish(DONE);
      if (ws == null) {
        return;
      }
      ws.request(1); // let the closing handshake be delivered
      synchronized (this) {
        send(ws, new ClientMessage.Complete(operationId, "complete"));
        // whenComplete, not thenRun: the socket must close even if an earlier send failed.
        sends.whenComplete((ignored, error) -> ws.sendClose(WebSocket.NORMAL_CLOSURE, ""));
      }
    }

    @Override
    public Integer length() {
      return null;
    }

    @Override
    public boolean isRepeatable() {
      return false;
    }

    @Override
    public InputStream asInputStream() {
      return InputStream.nullInputStream();
    }

    @Override
    public Reader asReader(Charset charset) {
      return Reader.nullReader();
    }

    /**
     * Feign closes the response body right after decoding, which for a detached subscription is a
     * no-op — the caller owns it from there. Still attached means the decoder never took ownership
     * (a {@code void} method, say), so the socket is closed here rather than leaked.
     */
    @Override
    public void close() {
      if (!detached.get()) {
        unsubscribe();
      }
    }

    private final class PayloadIterator implements Iterator<Map<String, Object>> {

      private final long timeoutMillis;

      private Object pending;

      PayloadIterator(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
      }

      @SuppressWarnings("unchecked")
      @Override
      public boolean hasNext() {
        if (pending == null) {
          pending = take();
        }
        if (pending instanceof Throwable error) {
          pending = DONE;
          throw asUnchecked(error);
        }
        return pending != DONE;
      }

      @SuppressWarnings("unchecked")
      @Override
      public Map<String, Object> next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        var payload = (Map<String, Object>) pending;
        pending = null;
        return payload;
      }

      private Object take() {
        try {
          var event =
              timeoutMillis <= 0
                  ? events.take()
                  : events.poll(timeoutMillis, TimeUnit.MILLISECONDS);
          if (event == null) {
            return new SocketTimeoutException(
                "no GraphQL subscription event within " + timeoutMillis + "ms");
          }
          if (event != DONE) {
            var ws = webSocket;
            if (ws != null) {
              ws.request(1); // consuming an event is what authorises the next read
            }
          }
          return event;
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return new InterruptedIOException("interrupted awaiting a GraphQL subscription event");
        }
      }
    }

    private static RuntimeException asUnchecked(Throwable error) {
      if (error instanceof RuntimeException runtime) {
        return runtime;
      }
      if (error instanceof IOException io) {
        return new UncheckedIOException(io);
      }
      return new IllegalStateException(error);
    }
  }
}
