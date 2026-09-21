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

import feign.Capability;
import feign.Client;
import feign.Contract;
import feign.Experimental;
import feign.RequestInterceptors;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.JsonCodec;
import feign.codec.JsonDecoder;
import feign.codec.JsonEncoder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@Experimental
public class GraphqlCapability implements Capability {

  private final GraphqlContract contract = new GraphqlContract();
  private final GraphqlEncoder graphqlEncoder;
  private final GraphqlDecoder graphqlDecoder;
  private final GraphqlRequestInterceptor interceptor;
  private final JsonEncoder jsonEncoder;
  private final JsonDecoder jsonDecoder;

  public GraphqlCapability(JsonCodec codec) {
    this(codec.encoder(), codec.decoder());
  }

  /**
   * @param eventTimeout how long a blocking subscription call waits for an event before failing
   *     with {@link java.net.SocketTimeoutException}; {@link java.time.Duration#ZERO} waits
   *     indefinitely. Does not apply to {@code Flow.Publisher} or {@code CompletableFuture}
   *     subscriptions, whose caller owns the deadline.
   */
  public GraphqlCapability(JsonCodec codec, Duration eventTimeout) {
    this(codec.encoder(), codec.decoder(), eventTimeout);
  }

  /**
   * @param executor runs the worker behind each {@code Flow.Publisher} and {@code
   *     CompletableFuture} subscription, and delivers to their subscribers. Supply your own to own
   *     the lifecycle; the default is bounded and daemon, and is never shut down.
   */
  public GraphqlCapability(JsonCodec codec, Duration eventTimeout, Executor executor) {
    this(codec.encoder(), codec.decoder(), eventTimeout, executor);
  }

  public GraphqlCapability(JsonEncoder encoder, JsonDecoder decoder) {
    this(encoder, decoder, GraphqlDecoder.DEFAULT_EVENT_TIMEOUT);
  }

  /**
   * @param eventTimeout see {@link #GraphqlCapability(JsonCodec, Duration)}
   */
  public GraphqlCapability(JsonEncoder encoder, JsonDecoder decoder, Duration eventTimeout) {
    this(encoder, decoder, eventTimeout, defaultExecutor());
  }

  /**
   * @param executor see {@link #GraphqlCapability(JsonCodec, Duration, Executor)}
   */
  public GraphqlCapability(
      JsonEncoder encoder, JsonDecoder decoder, Duration eventTimeout, Executor executor) {
    this.graphqlEncoder = new GraphqlEncoder(encoder, contract);
    this.graphqlDecoder = new GraphqlDecoder(decoder, eventTimeout, executor);
    this.interceptor = new GraphqlRequestInterceptor(encoder, contract);
    this.jsonEncoder = encoder;
    this.jsonDecoder = decoder;
  }

  /**
   * Each open {@code Flow.Publisher} or {@code CompletableFuture} subscription holds one worker for
   * its lifetime, so the default pool grows on demand and reaps idle threads rather than capping
   * concurrent subscriptions at a guess. Supply a bounded executor to cap them deliberately: the
   * excess is refused with {@code RejectedExecutionException} rather than left hanging.
   */
  private static Executor defaultExecutor() {
    var threads = new AtomicLong();
    return Executors.newCachedThreadPool(
        runnable -> {
          var thread =
              new Thread(runnable, "feign-graphql-subscription-" + threads.incrementAndGet());
          thread.setDaemon(true);
          return thread;
        });
  }

  @Override
  public Contract enrich(Contract contract) {
    return this.contract;
  }

  @Override
  public Encoder enrich(Encoder encoder) {
    return graphqlEncoder;
  }

  @Override
  public Decoder enrich(Decoder decoder) {
    return graphqlDecoder;
  }

  @Override
  public Client enrich(Client client) {
    return new GraphqlSubscriptionClient(client, contract, jsonEncoder, jsonDecoder);
  }

  @Override
  public RequestInterceptors enrich(RequestInterceptors requestInterceptors) {
    var enriched = new ArrayList<>(requestInterceptors.interceptors());
    enriched.add(interceptor);
    return new RequestInterceptors(enriched);
  }
}
