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

import com.google.common.base.Throwables;
import com.netflix.client.ClientException;
import com.netflix.client.ClientFactory;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ILoadBalancer;

import java.io.IOException;
import java.net.URI;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Provides;
import feign.Client;
import feign.Request;
import feign.Response;

/**
 * Adding this module will override URL resolution of {@link feign.Client Feign's client},
 * adding smart routing and resiliency capabilities provided by Ribbon.
 * <p/>
 * When using this, ensure the {@link feign.Target#url()} is set to as {@code http://clientName}
 * or {@code https://clientName}. {@link com.netflix.client.config.IClientConfig#getClientName() clientName}
 * will lookup the real url and port of your service dynamically.
 * <p/>
 * Ex.
 * <pre>
 * MyService api = Feign.create(MyService.class, "http://myAppProd", new RibbonModule());
 * </pre>
 * Where {@code myAppProd} is the ribbon client name and {@code myAppProd.ribbon.listOfServers} configuration
 * is set.
 */
@dagger.Module(overrides = true, library = true, complete = false)
public class RibbonModule {

  @Provides @Named("delegate") Client delegate(Client.Default delegate) {
    return delegate;
  }

  @Provides @Singleton Client httpClient(RibbonClient ribbon) {
    return ribbon;
  }

  @Singleton
  static class RibbonClient implements Client {
    private final Client delegate;

    @Inject
    public RibbonClient(@Named("delegate") Client delegate) {
      this.delegate = delegate;
    }

    @Override public Response execute(Request request, Request.Options options) throws IOException {
      try {
        URI asUri = URI.create(request.url());
        String clientName = asUri.getHost();
        URI uriWithoutSchemeAndPort = URI.create(request.url().replace(asUri.getScheme() + "://" + asUri.getHost(), ""));
        LBClient.RibbonRequest ribbonRequest = new LBClient.RibbonRequest(request, uriWithoutSchemeAndPort);
        return lbClient(clientName).executeWithLoadBalancer(ribbonRequest).toResponse();
      } catch (ClientException e) {
        throw Throwables.propagate(e);
      }
    }

    private LBClient lbClient(String clientName) {
      IClientConfig config = ClientFactory.getNamedConfig(clientName);
      ILoadBalancer lb = ClientFactory.getNamedLoadBalancer(clientName);
      return new LBClient(delegate, lb, config);
    }
  }
}
