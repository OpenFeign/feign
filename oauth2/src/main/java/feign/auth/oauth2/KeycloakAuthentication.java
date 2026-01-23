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

import com.auth0.jwt.algorithms.Algorithm;
import feign.auth.oauth2.core.AuthorizationGrantType;
import feign.auth.oauth2.core.ClientAuthenticationMethod;
import feign.auth.oauth2.core.registration.ClientRegistration;
import feign.auth.oauth2.core.registration.Credentials;
import feign.auth.oauth2.core.registration.ProviderDetails;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public final class KeycloakAuthentication extends OpenIdAuthentication {
  private KeycloakAuthentication(final ClientRegistration clientRegistration) {
    super(clientRegistration);
  }

  public static KeycloakAuthentication withClientSecretBasic(
      final String host, final String realm, final String clientId, final String clientSecret) {
    final ClientRegistration clientRegistration =
        ClientRegistration.builder()
            .credentials(
                Credentials.builder().clientId(clientId).clientSecret(clientSecret).build())
            .providerDetails(providerDetails(host, realm))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .build();
    return new KeycloakAuthentication(clientRegistration);
  }

  public static KeycloakAuthentication withClientSecretPost(
      final String host, final String realm, final String clientId, final String clientSecret) {
    final ClientRegistration clientRegistration =
        ClientRegistration.builder()
            .credentials(
                Credentials.builder().clientId(clientId).clientSecret(clientSecret).build())
            .providerDetails(providerDetails(host, realm))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .build();
    return new KeycloakAuthentication(clientRegistration);
  }

  public static KeycloakAuthentication withClientSecretJwt(
      final String host, final String realm, final String clientId, final String clientSecret) {
    final ClientRegistration clientRegistration =
        ClientRegistration.builder()
            .credentials(
                Credentials.builder().clientId(clientId).clientSecret(clientSecret).build())
            .providerDetails(providerDetails(host, realm))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_JWT)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .build();
    return new KeycloakAuthentication(clientRegistration);
  }

  public static KeycloakAuthentication withPrivateKeyJwt(
      final String host, final String realm, final String clientId, final String privateKeyBase64) {
    final byte[] privateKey = Base64.getDecoder().decode(privateKeyBase64);
    return withPrivateKeyJwt(host, realm, clientId, privateKey);
  }

  public static KeycloakAuthentication withPrivateKeyJwt(
      final String host, final String realm, final String clientId, final byte[] privateKey) {
    Algorithm signingAlgorithm = null;
    try {
      KeyFactory keyFactory = KeyFactory.getInstance("RSA");
      PKCS8EncodedKeySpec keySpecPKCS8 = new PKCS8EncodedKeySpec(privateKey);
      PrivateKey privKey = keyFactory.generatePrivate(keySpecPKCS8);
      signingAlgorithm = Algorithm.RSA256(null, (RSAPrivateKey) privKey);
    } catch (final NoSuchAlgorithmException | InvalidKeySpecException encryptionException) {
      throw new RuntimeException(encryptionException);
    }

    final ClientRegistration clientRegistration =
        ClientRegistration.builder()
            .credentials(
                Credentials.builder()
                    .clientId(clientId)
                    .jwtSignatureAlgorithm(signingAlgorithm)
                    .build())
            .providerDetails(providerDetails(host, realm))
            .clientAuthenticationMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .build();
    return new KeycloakAuthentication(clientRegistration);
  }

  private static ProviderDetails providerDetails(final String host, final String realm) {
    final String issuer = issuer(host, realm);
    return ProviderDetails.builder()
        .issuerUri(issuer)
        .authorizationUri(issuer + "/protocol/openid-connect/auth")
        .tokenUri(issuer + "/protocol/openid-connect/token")
        .userInfoUri(issuer + "/protocol/openid-connect/userinfo")
        .jwkSetUri(issuer + "/protocol/openid-connect/certs")
        .build();
  }

  private static String issuer(final String host, final String realm) {
    return String.format("%s/realms/%s", host, realm);
  }
}
