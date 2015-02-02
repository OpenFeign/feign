package feign.ribbon;

import com.netflix.client.ClientException;
import com.netflix.client.ClientFactory;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ILoadBalancer;

import java.io.IOException;
import java.net.URI;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import dagger.Lazy;
import feign.Client;
import feign.Request;
import feign.Response;

/**
 * RibbonClient can be used in Fiegn builder to activate smart routing and resiliency capabilities
 * provided by Ribbon. Ex.
 * <pre>
 * MyService api = Feign.builder.client(new RibbonClient()).target(MyService.class,
 * "http://myAppProd");
 * </pre>
 * Where {@code myAppProd} is the ribbon client name and {@code myAppProd.ribbon.listOfServers}
 * configuration is set.
 */
public class RibbonClient implements Client {

  private final Client delegate;

  public RibbonClient() {
    this.delegate = new Client.Default(
        new Lazy<SSLSocketFactory>() {
          public SSLSocketFactory get() {
            return (SSLSocketFactory) SSLSocketFactory.getDefault();
          }
        },
        new Lazy<HostnameVerifier>() {
          public HostnameVerifier get() {
            return HttpsURLConnection.getDefaultHostnameVerifier();
          }
        }
    );
  }

  public RibbonClient(Client delegate) {
    this.delegate = delegate;
  }

  @Override
  public Response execute(Request request, Request.Options options) throws IOException {
    try {
      URI asUri = URI.create(request.url());
      String clientName = asUri.getHost();
      URI
          uriWithoutSchemeAndPort =
          URI.create(request.url().replace(asUri.getScheme() + "://" + asUri.getHost(), ""));
      LBClient.RibbonRequest
          ribbonRequest =
          new LBClient.RibbonRequest(request, uriWithoutSchemeAndPort);
      return lbClient(clientName).executeWithLoadBalancer(ribbonRequest).toResponse();
    } catch (ClientException e) {
      if (e.getCause() instanceof IOException) {
        throw IOException.class.cast(e.getCause());
      }
      throw new RuntimeException(e);
    }
  }

  private LBClient lbClient(String clientName) {
    IClientConfig config = ClientFactory.getNamedConfig(clientName);
    ILoadBalancer lb = ClientFactory.getNamedLoadBalancer(clientName);
    return new LBClient(delegate, lb, config);
  }
}
