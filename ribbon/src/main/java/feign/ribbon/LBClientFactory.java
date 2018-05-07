/**
 * Copyright 2012-2018 The Feign Authors
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

import com.netflix.client.ClientFactory;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.loadbalancer.ILoadBalancer;

public interface LBClientFactory {

  LBClient create(String clientName);

  /**
   * Uses {@link ClientFactory} static factories from ribbon to create an LBClient.
   */
  public static final class Default implements LBClientFactory {
    @Override
    public LBClient create(String clientName) {
      IClientConfig config =
          ClientFactory.getNamedConfig(clientName, DisableAutoRetriesByDefaultClientConfig.class);
      ILoadBalancer lb = ClientFactory.getNamedLoadBalancer(clientName);
      return LBClient.create(lb, config);
    }
  }

  IClientConfigKey<String> RetryableStatusCodes =
      new CommonClientConfigKey<String>("RetryableStatusCodes") {};

  final class DisableAutoRetriesByDefaultClientConfig extends DefaultClientConfigImpl {
    @Override
    public int getDefaultMaxAutoRetriesNextServer() {
      return 0;
    }

    @Override
    public void loadDefaultValues() {
      super.loadDefaultValues();
      putDefaultStringProperty(LBClientFactory.RetryableStatusCodes, "");
    }
  }
}
