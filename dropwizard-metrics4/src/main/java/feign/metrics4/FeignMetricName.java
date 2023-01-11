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
package feign.metrics4;


import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import com.codahale.metrics.MetricRegistry;
import feign.MethodMetadata;
import feign.Target;

public final class FeignMetricName {

  private final Class<?> meteredComponent;


  public FeignMetricName(Class<?> meteredComponent) {
    this.meteredComponent = meteredComponent;
  }


  public String metricName(MethodMetadata methodMetadata, Target<?> target, String suffix) {
    return MetricRegistry.name(metricName(methodMetadata, target), suffix);
  }

  public String metricName(MethodMetadata methodMetadata, Target<?> target) {
    return metricName(methodMetadata.targetType(), methodMetadata.method(), target.url());
  }

  public String metricName(Class<?> targetType, Method method, String url) {
    return MetricRegistry.name(meteredComponent, targetType.getName(), method.getName(),
        extractHost(url));
  }

  private String extractHost(final String targetUrl) {
    try {
      return new URI(targetUrl).getHost();
    } catch (final URISyntaxException e) {
      // can't get the host, in that case, just read first 20 chars from url
      return targetUrl.length() <= 20
          ? targetUrl
          : targetUrl.substring(0, 20);
    }
  }


}
