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
package feign.ribbon;

import com.netflix.loadbalancer.AbstractLoadBalancer;
import com.netflix.loadbalancer.Server;
import java.net.URI;
import feign.Request;
import feign.RequestTemplate;
import feign.Target;
import static com.netflix.client.ClientFactory.getNamedLoadBalancer;
import static feign.Util.checkNotNull;
import static java.lang.String.format;

/**
 * Basic integration for {@link com.netflix.loadbalancer.ILoadBalancer loadbalancer-aware} targets.
 * Using this will enable dynamic url discovery via ribbon including incrementing server request
 * counts. <br>
 * Ex.
 * 
 * <pre>
 * MyService api = Feign.builder().target(LoadBalancingTarget.create(MyService.class,
 * "http://myAppProd"))
 * </pre>
 * 
 * Where {@code myAppProd} is the ribbon loadbalancer name and {@code
 * myAppProd.ribbon.listOfServers} configuration is set.
 *
 * @param <T> corresponds to {@link feign.Target#type()}
 */
public class LoadBalancingTarget<T> implements Target<T> {

  private final String name;
  private final String scheme;
  private final String path;
  private final Class<T> type;
  private final AbstractLoadBalancer lb;

  /**
   * @Deprecated will be removed in Feign 10
   */
  @Deprecated
  protected LoadBalancingTarget(Class<T> type, String scheme, String name) {
    this.type = checkNotNull(type, "type");
    this.scheme = checkNotNull(scheme, "scheme");
    this.name = checkNotNull(name, "name");
    this.path = "";
    this.lb = AbstractLoadBalancer.class.cast(getNamedLoadBalancer(name()));
  }

  protected LoadBalancingTarget(Class<T> type, String scheme, String name, String path) {
    this.type = checkNotNull(type, "type");
    this.scheme = checkNotNull(scheme, "scheme");
    this.name = checkNotNull(name, "name");
    this.path = checkNotNull(path, "path");
    this.lb = AbstractLoadBalancer.class.cast(getNamedLoadBalancer(name()));
  }

  /**
   * Creates a target which dynamically derives urls from a
   * {@link com.netflix.loadbalancer.ILoadBalancer loadbalancer}.
   *
   * @param type corresponds to {@link feign.Target#type()}
   * @param url naming convention is {@code https://name} or {@code http://name/api/v2} where name
   *        corresponds to {@link com.netflix.client.ClientFactory#getNamedLoadBalancer(String)}
   */
  public static <T> LoadBalancingTarget<T> create(Class<T> type, String url) {
    URI asUri = URI.create(url);
    return new LoadBalancingTarget<T>(type, asUri.getScheme(), asUri.getHost(), asUri.getPath());
  }

  @Override
  public Class<T> type() {
    return type;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public String url() {
    return String.format("%s://%s", scheme, path);
  }

  /**
   * current load balancer for the target.
   */
  public AbstractLoadBalancer lb() {
    return lb;
  }

  @Override
  public Request apply(RequestTemplate input) {
    Server currentServer = lb.chooseServer(null);
    String url = format("%s://%s%s", scheme, currentServer.getHostPort(), path);
    input.target(url);
    try {
      return input.request();
    } finally {
      lb.getLoadBalancerStats().incrementNumRequests(currentServer);
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof LoadBalancingTarget) {
      LoadBalancingTarget<?> other = (LoadBalancingTarget<?>) obj;
      return type.equals(other.type)
          && name.equals(other.name);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + type.hashCode();
    result = 31 * result + name.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "LoadBalancingTarget(type=" + type.getSimpleName() + ", name=" + name + ", path=" + path
        + ")";
  }
}
