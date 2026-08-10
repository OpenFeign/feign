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

  public GraphqlCapability(JsonEncoder encoder, JsonDecoder decoder) {
    this(encoder, decoder, GraphqlDecoder.DEFAULT_EVENT_TIMEOUT);
  }

  /**
   * @param eventTimeout see {@link #GraphqlCapability(JsonCodec, Duration)}
   */
  public GraphqlCapability(JsonEncoder encoder, JsonDecoder decoder, Duration eventTimeout) {
    this.graphqlEncoder = new GraphqlEncoder(encoder, contract);
    this.graphqlDecoder = new GraphqlDecoder(decoder, eventTimeout);
    this.interceptor = new GraphqlRequestInterceptor(encoder, contract);
    this.jsonEncoder = encoder;
    this.jsonDecoder = decoder;
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
