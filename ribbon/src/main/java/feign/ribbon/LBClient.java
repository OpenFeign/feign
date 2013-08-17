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

import com.netflix.client.AbstractLoadBalancerAwareClient;
import com.netflix.client.ClientException;
import com.netflix.client.ClientRequest;
import com.netflix.client.IResponse;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.util.Pair;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Map;

import feign.Client;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import feign.RetryableException;

import static com.netflix.client.config.CommonClientConfigKey.ConnectTimeout;
import static com.netflix.client.config.CommonClientConfigKey.ReadTimeout;

class LBClient extends AbstractLoadBalancerAwareClient<LBClient.RibbonRequest, LBClient.RibbonResponse> {

  private final Client delegate;
  private final int connectTimeout;
  private final int readTimeout;

  LBClient(Client delegate, ILoadBalancer lb, IClientConfig clientConfig) {
    this.delegate = delegate;
    this.connectTimeout = Integer.valueOf(clientConfig.getProperty(ConnectTimeout).toString());
    this.readTimeout = Integer.valueOf(clientConfig.getProperty(ReadTimeout).toString());
    setLoadBalancer(lb);
    initWithNiwsConfig(clientConfig);
  }

  @Override
  public RibbonResponse execute(RibbonRequest request) throws IOException {
    int connectTimeout = config(request, ConnectTimeout, this.connectTimeout);
    int readTimeout = config(request, ReadTimeout, this.readTimeout);

    Request.Options options = new Request.Options(connectTimeout, readTimeout);
    Response response = delegate.execute(request.toRequest(), options);
    return new RibbonResponse(request.getUri(), response);
  }

  @Override protected boolean isCircuitBreakerException(Exception e) {
    return e instanceof IOException;
  }

  @Override protected boolean isRetriableException(Exception e) {
    return e instanceof RetryableException;
  }

  @Override
  protected Pair<String, Integer> deriveSchemeAndPortFromPartialUri(RibbonRequest task) {
    return new Pair<String, Integer>(URI.create(task.request.url()).getScheme(), task.getUri().getPort());
  }

  @Override protected int getDefaultPort() {
    return 443;
  }

  static class RibbonRequest extends ClientRequest implements Cloneable {

    private final Request request;

    RibbonRequest(Request request, URI uri) {
      this.request = request;
      setUri(uri);
    }

    Request toRequest() {
      return new RequestTemplate()
          .method(request.method())
          .append(getUri().toASCIIString())
          .headers(request.headers())
          .body(request.body()).request();
    }

    public Object clone() {
      return new RibbonRequest(request, getUri());
    }
  }

  static class RibbonResponse implements IResponse {

    private final URI uri;
    private final Response response;

    RibbonResponse(URI uri, Response response) {
      this.uri = uri;
      this.response = response;
    }

    @Override public Object getPayload() throws ClientException {
      return response.body();
    }

    @Override public boolean hasPayload() {
      return response.body() != null;
    }

    @Override public boolean isSuccess() {
      return response.status() == 200;
    }

    @Override public URI getRequestedURI() {
      return uri;
    }

    @Override public Map<String, Collection<String>> getHeaders() {
      return response.headers();
    }

    Response toResponse() {
      return response;
    }
  }

  static int config(RibbonRequest request, CommonClientConfigKey key, int defaultValue) {
    if (request.getOverrideConfig() != null && request.getOverrideConfig().containsProperty(key))
      return Integer.valueOf(request.getOverrideConfig().getProperty(key).toString());
    return defaultValue;
  }
}
