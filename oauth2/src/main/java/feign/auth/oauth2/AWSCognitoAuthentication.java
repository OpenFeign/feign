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

public final class AWSCognitoAuthentication extends OpenIdAuthentication {
  private static final String COGNITO_HOST_TEMPLATE = "https://%s.auth.%s.amazoncognito.com";
  private static final String COGNITO_ISSUER_TEMPLATE = "https://cognito-idp.%s.amazonaws.com/%s";

  public AWSCognitoAuthentication(
      final String domain, final String region, final String clientId, final String clientSecret) {
    super(clientRegistration(domain, region, clientId, clientSecret, null));
  }

  public AWSCognitoAuthentication(
      final String domain,
      final String region,
      final String clientId,
      final String clientSecret,
      final String tenant) {
    super(clientRegistration(domain, region, clientId, clientSecret, tenant));
  }

  private static ClientRegistration clientRegistration(
      final String domain,
      final String region,
      final String clientId,
      final String clientSecret,
      final String tenant) {
    final String cognitoHost = String.format(COGNITO_HOST_TEMPLATE, domain, region);
    ProviderDetails.Builder detailsBuilder =
        ProviderDetails.builder()
            .authorizationUri(cognitoHost + "/oauth2/authorize")
            .tokenUri(cognitoHost + "/oauth2/token")
            .userInfoUri(cognitoHost + "/oauth2/userInfo");

    if (tenant != null) {
      final String issuer = String.format(COGNITO_ISSUER_TEMPLATE, region, tenant);
      detailsBuilder =
          detailsBuilder.issuerUri(issuer).jwkSetUri(issuer + "/.well-known/jwks.json");
    }

    return ClientRegistration.builder()
        .credentials(Credentials.builder().clientId(clientId).clientSecret(clientSecret).build())
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .providerDetails(detailsBuilder.build())
        .build();
  }
}
