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
package feign.micrometer;

import feign.AsyncClient;
import feign.Capability;
import feign.Client;
import feign.InvocationHandlerFactory;
import feign.codec.Decoder;
import feign.codec.Encoder;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MicrometerCapability implements Capability {

  private final MeterRegistry meterRegistry;

  public MicrometerCapability() {
    this(new SimpleMeterRegistry(SimpleConfig.DEFAULT, Clock.SYSTEM));
    Metrics.addRegistry(meterRegistry);
  }

  public MicrometerCapability(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public MicrometerCapability(MeterRegistry meterRegistry, List<Tag> tagsList) {
    meterRegistry.config().commonTags(tagsList);
    this.meterRegistry = meterRegistry;
  }

  public MicrometerCapability(MeterRegistry meterRegistry, Map<String, String> tagsMap) {
    List<Tag> tagsList = mapTags(tagsMap);
    meterRegistry.config().commonTags(tagsList);
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Client enrich(Client client) {
    return new MeteredClient(client, meterRegistry);
  }

  @Override
  public AsyncClient<Object> enrich(AsyncClient<Object> client) {
    return new MeteredAsyncClient(client, meterRegistry);
  }

  @Override
  public Encoder enrich(Encoder encoder) {
    return new MeteredEncoder(encoder, meterRegistry);
  }

  @Override
  public Decoder enrich(Decoder decoder) {
    return new MeteredDecoder(decoder, meterRegistry);
  }

  @Override
  public InvocationHandlerFactory enrich(InvocationHandlerFactory invocationHandlerFactory) {
    return new MeteredInvocationHandleFactory(invocationHandlerFactory, meterRegistry);
  }

  private List<Tag> mapTags(Map<String, String> tags) {
    return tags.keySet().stream()
        .map(tagKey -> Tag.of(tagKey, tags.get(tagKey)))
        .collect(Collectors.toList());
  }
}
