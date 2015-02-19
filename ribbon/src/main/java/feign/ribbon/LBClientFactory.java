package feign.ribbon;

import com.netflix.client.ClientFactory;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ILoadBalancer;

public interface LBClientFactory {

  LBClient create(String clientName);

  /**
   * Uses {@link ClientFactory} static factories from ribbon to create an LBClient.
   */
  public static final class Default implements LBClientFactory {
    @Override
    public LBClient create(String clientName) {
      IClientConfig config = ClientFactory.getNamedConfig(clientName);
      ILoadBalancer lb = ClientFactory.getNamedLoadBalancer(clientName);
      return LBClient.create(lb, config);
    }
  }
}
