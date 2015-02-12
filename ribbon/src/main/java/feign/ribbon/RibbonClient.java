package feign.ribbon;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import com.netflix.client.ClientFactory;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ILoadBalancer;

import feign.Client;
import feign.Request;
import feign.Response;

/**
 * RibbonClient can be used in Feign builder to activate smart routing and resiliency capabilities
 * provided by Ribbon. Ex.
 * <p>
 * 
 * <pre>
 * MyService api = Feign.builder.client(RibbonClient.DEFAULT).target(MyService.class,
 *     &quot;http://myAppProd&quot;);
 * </pre>
 * 
 * <p>
 * Where {@code myAppProd} is the ribbon client name and {@code myAppProd.ribbon.listOfServers}
 * configuration is set.
 */
public class RibbonClient implements Client {

  public static RibbonClient DEFAULT = new RibbonClient();
  
  private volatile Map<String, LBClient> lbClientCache = new LinkedHashMap<String, LBClient>();

  private final Client delegate;

  public RibbonClient() {
    this.delegate = new Client.Default(null, null);
  }

  public RibbonClient(Client delegate) {
    this.delegate = delegate;
  }

  @Override
  public Response execute(Request request, Request.Options options) throws IOException {
    try {
      URI asUri = URI.create(request.url());
      String clientName = asUri.getHost();
      URI uriWithoutHost = URI.create(request.url().replace(asUri.getHost(), ""));
      LBClient.RibbonRequest ribbonRequest =
          new LBClient.RibbonRequest(request, uriWithoutHost, delegate);
      return getOrCreateLBClient(clientName).executeWithLoadBalancer(ribbonRequest,
          new FeignOptionsClientConfig(options))
          .toResponse();
    } catch (Exception e) {
      if (e.getCause() instanceof IOException) {
        throw IOException.class.cast(e.getCause());
      }
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns a cached version of the {@link LBClient} if it exists otherwise a new {@link LBClient}
   * is created and added to the cache.
   * <p>
   * Ribbon does not expect multiple LBClient instances for the same client. This introduces a cache
   * to avoid redundantly creating them.
   * 
   * @param clientName - The clientName that the {@link LBClient} will be cache against
   * @return
   */
  private LBClient getOrCreateLBClient(String clientName) {
    if (lbClientCache.containsKey(clientName)) {
      return lbClientCache.get(clientName);
    } else {
      synchronized (this) {
        LBClient lbClient = lbClientCache.get(clientName);
        if (lbClient == null) {
          IClientConfig config = ClientFactory.getNamedConfig(clientName);
          ILoadBalancer lb = ClientFactory.getNamedLoadBalancer(clientName);
          lbClient = new LBClient(lb, config);
          lbClientCache.put(clientName, lbClient);
        }
        return lbClient;
      }
    }
  }

  static class FeignOptionsClientConfig extends DefaultClientConfigImpl {

    public FeignOptionsClientConfig(Request.Options options) {
      setProperty(CommonClientConfigKey.ConnectTimeout, options.connectTimeoutMillis());
      setProperty(CommonClientConfigKey.ReadTimeout, options.readTimeoutMillis());
    }

    @Override
    public void loadProperties(String clientName) {

    }

    @Override
    public void loadDefaultValues() {

    }

  }
  
}
