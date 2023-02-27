/*
 * Copyright 2012-2023 The Feign Authors
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
package feign.micrometer;

import feign.AsyncFeign;
import feign.Feign;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.Before;

public class MicrometerObservationRegistryCapabilityTest extends MicrometerCapabilityTest {

  ObservationRegistry observationRegistry = ObservationRegistry.create();

  @Before
  public void setupRegistry() {
    observationRegistry.observationConfig()
        .observationHandler(new DefaultMeterObservationHandler(metricsRegistry));
  }

  @Override
  protected Feign.Builder customizeBuilder(Feign.Builder builder) {
    return super.customizeBuilder(builder)
        .addCapability(new MicrometerObservationCapability(this.observationRegistry));
  }

  @Override
  protected <C> AsyncFeign.AsyncBuilder<C> customizeBuilder(AsyncFeign.AsyncBuilder<C> builder) {
    return super.customizeBuilder(builder)
        .addCapability(new MicrometerObservationCapability(this.observationRegistry));
  }
}
