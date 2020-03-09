/**
 * Copyright 2012-2020 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.metrics5;

import feign.Capability;
import feign.Client;
import feign.InvocationHandlerFactory;
import feign.codec.Decoder;
import feign.codec.Encoder;
import io.dropwizard.metrics5.MetricRegistry;
import io.dropwizard.metrics5.SharedMetricRegistries;

public class Metrics5Capability implements Capability {

  private final MetricRegistry metricRegistry;
  private final MetricSuppliers metricSuppliers;

  public Metrics5Capability() {
    this(SharedMetricRegistries.getOrCreate("feign"), new MetricSuppliers());
  }

  public Metrics5Capability(MetricRegistry metricRegistry) {
    this(metricRegistry, new MetricSuppliers());
  }

  public Metrics5Capability(MetricRegistry metricRegistry, MetricSuppliers metricSuppliers) {
    this.metricRegistry = metricRegistry;
    this.metricSuppliers = metricSuppliers;
  }

  @Override
  public Client enrich(Client client) {
    return new MeteredClient(client, metricRegistry, metricSuppliers);
  }

  @Override
  public Encoder enrich(Encoder encoder) {
    return new MeteredEncoder(encoder, metricRegistry, metricSuppliers);
  }

  @Override
  public Decoder enrich(Decoder decoder) {
    return new MeteredDecoder(decoder, metricRegistry, metricSuppliers);
  }

  @Override
  public InvocationHandlerFactory enrich(InvocationHandlerFactory invocationHandlerFactory) {
    return new MeteredInvocationHandleFactory(invocationHandlerFactory, metricRegistry,
        metricSuppliers);
  }

}
