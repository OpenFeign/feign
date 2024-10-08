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
package feign.metrics5;

import io.dropwizard.metrics5.*;
import io.dropwizard.metrics5.MetricRegistry.MetricSupplier;
import java.util.concurrent.TimeUnit;

public class MetricSuppliers {

  public MetricSupplier<Timer> timers() {
    // only keep timer data for 1 minute
    return () -> new Timer(new SlidingTimeWindowArrayReservoir(1, TimeUnit.MINUTES));
  }

  public MetricSupplier<Meter> meters() {
    return () -> new Meter();
  }

  public MetricSupplier<Histogram> histograms() {
    // only keep timer data for 1 minute
    return () -> new Histogram(new SlidingTimeWindowArrayReservoir(1, TimeUnit.MINUTES));
  }
}
