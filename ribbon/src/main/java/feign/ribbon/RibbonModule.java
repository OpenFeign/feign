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


import dagger.Provides;
import feign.Client;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Adding this module will override URL resolution of {@link feign.Client Feign's client}, adding
 * smart routing and resiliency capabilities provided by Ribbon. <br>
 * When using this, ensure the {@link feign.Target#url()} is set to as {@code http://clientName} or
 * {@code https://clientName}. {@link com.netflix.client.config.IClientConfig#getClientName()
 * clientName} will lookup the real url and port of your service dynamically. <br>
 * Ex.
 *
 * <pre>
 * MyService api = Feign.create(MyService.class, "http://myAppProd", new RibbonModule());
 * </pre>
 *
 * Where {@code myAppProd} is the ribbon client name and {@code myAppProd.ribbon.listOfServers}
 * configuration is set.
 */
@dagger.Module(overrides = true, library = true, complete = false)
public class RibbonModule {

  @Provides
  @Named("delegate")
  Client delegate(Client.Default delegate) {
    return delegate;
  }

  @Provides
  @Singleton
  Client httpClient(@Named("delegate") Client client) {
    return new RibbonClient(client);
  }
}
