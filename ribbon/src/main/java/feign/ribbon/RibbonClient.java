package feign.ribbon;

import java.io.IOException;
import java.net.URI;

import com.netflix.client.ClientException;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;

import feign.Client;
import feign.Request;
import feign.Response;

/**
 * RibbonClient can be used in Feign builder to activate smart routing and resiliency capabilities
 * provided by Ribbon. Ex.
 * 
 * <pre>
 * MyService api = Feign.builder.client(RibbonClient.create()).target(MyService.class,
 *     &quot;http://myAppProd&quot;);
 * </pre>
 * 
 * Where {@code myAppProd} is the ribbon client name and {@code myAppProd.ribbon.listOfServers}
 * configuration is set.
 */
public class RibbonClient implements Client {

  private final Client delegate;
  private final LBClientFactory lbClientFactory;


  public static RibbonClient create() {
    return builder().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * @deprecated Use the {@link RibbonClient#create()}
   */
  @Deprecated
  public RibbonClient() {
    this(new Client.Default(null, null));
  }

  /**
   * @deprecated Use the {@link RibbonClient#create()}
   */
  @Deprecated
  public RibbonClient(Client delegate) {
    this(delegate, new LBClientFactory.Default());
  }

  RibbonClient(Client delegate, LBClientFactory lbClientFactory) {
    this.delegate = delegate;
    this.lbClientFactory = lbClientFactory;
  }

  @Override
  public Response execute(Request request, Request.Options options) throws IOException {
    try {
      URI asUri = URI.create(request.url());
      String clientName = asUri.getHost();
      URI uriWithoutHost = cleanUrl(request.url(), clientName);
      LBClient.RibbonRequest ribbonRequest =
          new LBClient.RibbonRequest(delegate, request, uriWithoutHost);
      return lbClient(clientName).executeWithLoadBalancer(ribbonRequest,
          new FeignOptionsClientConfig(options)).toResponse();
    } catch (ClientException e) {
      if (e.getCause() instanceof IOException) {
        throw IOException.class.cast(e.getCause());
      }
      throw new RuntimeException(e);
    }
  }

  static URI cleanUrl(String originalUrl, String host) {
    return URI.create(originalUrl.replaceFirst(host, ""));
  }

  private LBClient lbClient(String clientName) {
    return lbClientFactory.create(clientName);
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
  
  public static final class Builder {

    Builder() {
    }

    private Client delegate;
    private LBClientFactory lbClientFactory;

    public Builder delegate(Client delegate) {
      this.delegate = delegate;
      return this;
    }

    public Builder lbClientFactory(LBClientFactory lbClientFactory) {
      this.lbClientFactory = lbClientFactory;
      return this;
    }

    public RibbonClient build() {
      return new RibbonClient(
          delegate != null ? delegate : new Client.Default(null, null),
          lbClientFactory != null ? lbClientFactory : new LBClientFactory.Default()
      );
    }
  }
}
