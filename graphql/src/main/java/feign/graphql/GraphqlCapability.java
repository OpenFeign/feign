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
import feign.Contract;
import feign.Experimental;
import feign.RequestInterceptors;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.JsonCodec;
import feign.codec.JsonDecoder;
import feign.codec.JsonEncoder;
import java.util.ArrayList;

@Experimental
public class GraphqlCapability implements Capability {

  private final GraphqlContract contract = new GraphqlContract();
  private final GraphqlEncoder graphqlEncoder;
  private final GraphqlDecoder graphqlDecoder;
  private final GraphqlRequestInterceptor interceptor;

  public GraphqlCapability(JsonCodec codec) {
    this(codec.encoder(), codec.decoder());
  }

  public GraphqlCapability(JsonEncoder encoder, JsonDecoder decoder) {
    this.graphqlEncoder = new GraphqlEncoder(encoder, contract);
    this.graphqlDecoder = new GraphqlDecoder(decoder);
    this.interceptor = new GraphqlRequestInterceptor(encoder, contract);
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
  public RequestInterceptors enrich(RequestInterceptors requestInterceptors) {
    var enriched = new ArrayList<>(requestInterceptors.interceptors());
    enriched.add(interceptor);
    return new RequestInterceptors(enriched);
  }
}
