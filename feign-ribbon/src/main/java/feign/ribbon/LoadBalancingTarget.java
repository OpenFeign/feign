/*
 * Copyright 2013 Netflix, Inc.
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
package feign.ribbon;

import com.google.common.base.Objects;
import com.netflix.loadbalancer.AbstractLoadBalancer;
import com.netflix.loadbalancer.Server;

import java.net.URI;

import feign.Request;
import feign.RequestTemplate;
import feign.Target;

import static com.google.common.base.Objects.equal;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.netflix.client.ClientFactory.getNamedLoadBalancer;
import static java.lang.String.format;

/**
 * Basic integration for {@link com.netflix.loadbalancer.ILoadBalancer loadbalancer-aware} targets.
 * Using this will enable dynamic url discovery via ribbon including incrementing server request counts.
 * <p/>
 * Ex.
 * <pre>
 * MyService api = Feign.create(LoadBalancingTarget.create(MyService.class, "http://myAppProd"))
 * </pre>
 * Where {@code myAppProd} is the ribbon loadbalancer name and {@code myAppProd.ribbon.listOfServers} configuration
 * is set.
 *
 * @param <T> corresponds to {@link feign.Target#type()}
 */
public class LoadBalancingTarget<T> implements Target<T> {

  /**
   * creates a target which dynamically derives urls from a {@link com.netflix.loadbalancer.ILoadBalancer loadbalancer}.
   *
   * @param type       corresponds to {@link feign.Target#type()}
   * @param schemeName naming convention is {@code https://name} or {@code http://name} where
   *                   name corresponds to {@link com.netflix.client.ClientFactory#getNamedLoadBalancer(String)}
   */
  public static <T> LoadBalancingTarget<T> create(Class<T> type, String schemeName) {
    URI asUri = URI.create(schemeName);
    return new LoadBalancingTarget<T>(type, asUri.getScheme(), asUri.getHost());
  }

  private final String name;
  private final String scheme;
  private final Class<T> type;
  private final AbstractLoadBalancer lb;

  protected LoadBalancingTarget(Class<T> type, String scheme, String name) {
    this.type = checkNotNull(type, "type");
    this.scheme = checkNotNull(scheme, "scheme");
    this.name = checkNotNull(name, "name");
    this.lb = AbstractLoadBalancer.class.cast(getNamedLoadBalancer(name()));
  }

  @Override public Class<T> type() {
    return type;
  }

  @Override public String name() {
    return name;
  }

  @Override public String url() {
    return name;
  }

  /**
   * current load balancer for the target.
   */
  public AbstractLoadBalancer lb() {
    return lb;
  }

  @Override public Request apply(RequestTemplate input) {
    Server currentServer = lb.chooseServer(null);
    String url = format("%s://%s", scheme, currentServer.getHostPort());
    input.insert(0, url);
    try {
      return input.request();
    } finally {
      lb.getLoadBalancerStats().incrementNumRequests(currentServer);
    }
  }

  @Override public int hashCode() {
    return Objects.hashCode(type, name);
  }

  @Override public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (LoadBalancingTarget.class != obj.getClass())
      return false;
    LoadBalancingTarget<?> that = LoadBalancingTarget.class.cast(obj);
    return equal(this.type, that.type) && equal(this.name, that.name);
  }
}
