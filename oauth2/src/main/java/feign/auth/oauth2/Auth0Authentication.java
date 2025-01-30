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

import feign.auth.oauth2.core.AuthorizationGrantType;
import feign.auth.oauth2.core.ClientAuthenticationMethod;
import feign.auth.oauth2.core.registration.ClientRegistration;
import feign.auth.oauth2.core.registration.Credentials;
import feign.auth.oauth2.core.registration.ProviderDetails;

public final class Auth0Authentication extends OpenIdAuthentication {
  private static final String AUTH0_HOST_TEMPLATE = "https://%s.auth0.com";

  public Auth0Authentication(
      final String clientId,
      final String clientSecret,
      final String tenant,
      final String audience) {
    super(clientRegistration(clientId, clientSecret, tenant, audience));
  }

  private static ClientRegistration clientRegistration(
      final String clientId,
      final String clientSecret,
      final String tenant,
      final String audience) {
    final String auth0Host = String.format(AUTH0_HOST_TEMPLATE, tenant);

    return ClientRegistration.builder()
        .credentials(Credentials.builder().clientId(clientId).clientSecret(clientSecret).build())
        .audience(audience)
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .providerDetails(
            ProviderDetails.builder()
                .authorizationUri(auth0Host + "/authorize")
                .tokenUri(auth0Host + "/oauth/token")
                .userInfoUri(auth0Host + "/userInfo")
                .jwkSetUri(auth0Host + "/.well-known/jwks.json")
                .build())
        .build();
  }
}
