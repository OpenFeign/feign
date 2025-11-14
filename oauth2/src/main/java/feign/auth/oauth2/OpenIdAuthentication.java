/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
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
package feign.auth.oauth2;

import feign.BaseBuilder;
import feign.auth.oauth2.core.clients.OpenIdProviderClient;
import feign.auth.oauth2.core.registration.ClientRegistration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class OpenIdAuthentication extends OAuth2Authentication {
  private final boolean discovery;

  protected OpenIdAuthentication(final ClientRegistration clientRegistration) {
    this(clientRegistration, false);
  }

  private OpenIdAuthentication(
      final ClientRegistration clientRegistration, final boolean discovery) {
    super(clientRegistration);
    this.discovery = discovery;
  }

  @Override
  public <B extends BaseBuilder<B, T>, T> B beforeBuild(final B baseBuilder) {
    final B builder = super.beforeBuild(baseBuilder);

    idpClient = new OpenIdProviderClient(httpClient, httpOptions, jsonDecoder);
    if (discovery) {
      try {
        clientRegistration =
            ((OpenIdProviderClient) idpClient)
                .discover(clientRegistration)
                .get(httpOptions.readTimeout(), httpOptions.readTimeoutUnit());
      } catch (final InterruptedException | ExecutionException | TimeoutException authException) {
        throw new RuntimeException(authException);
      }
    }

    return builder;
  }

  public static OpenIdAuthentication discover(final ClientRegistration clientRegistration) {
    if (clientRegistration.getProviderDetails().getIssuerUri() == null
        || clientRegistration.getProviderDetails().getIssuerUri().isEmpty()) {
      throw new IllegalArgumentException("issuer cannot be empty");
    }

    if (clientRegistration.getCredentials().getClientId() == null
        || clientRegistration.getCredentials().getClientId().isEmpty()) {
      throw new IllegalArgumentException("clientId cannot be empty");
    }

    final boolean clientSecretPresent =
        clientRegistration.getCredentials().getClientSecret() != null
            && !clientRegistration.getCredentials().getClientSecret().isEmpty();
    if (!clientSecretPresent
        && clientRegistration.getCredentials().getJwtSignatureAlgorithm() == null) {
      throw new IllegalArgumentException(
          "clientSecret of jwtSignatureAlgorithm should be provided");
    }

    return new OpenIdAuthentication(clientRegistration, true);
  }
}
