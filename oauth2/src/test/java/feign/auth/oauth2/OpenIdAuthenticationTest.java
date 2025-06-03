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

import static org.assertj.core.api.Assertions.assertThat;

import com.auth0.jwt.algorithms.Algorithm;
import feign.Feign;
import feign.auth.oauth2.core.ClientAuthenticationMethod;
import feign.auth.oauth2.core.registration.ClientRegistration;
import feign.auth.oauth2.core.registration.Credentials;
import feign.auth.oauth2.core.registration.ProviderDetails;
import feign.auth.oauth2.mock.IcecreamClient;
import feign.auth.oauth2.mock.domain.Mixin;
import feign.auth.oauth2.support.KeyUtils;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import org.junit.jupiter.api.Test;

class OpenIdAuthenticationTest extends AbstractKeycloakTest {

  @Test
  void testClientSecretBasic() {
    OpenIdAuthentication openIdAuthenticator =
        OpenIdAuthentication.discover(
            ClientRegistration.builder()
                .credentials(
                    Credentials.builder()
                        .clientId(KeyCloakCredentials.FEIGN_CLIENT_ID_WITH_SECRET)
                        .clientSecret(KeyCloakCredentials.CLIENT_SECRET_CREDENTIALS)
                        .build())
                .providerDetails(ProviderDetails.builder().issuerUri(issuer()).build())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .build());

    IcecreamClient client =
        Feign.builder()
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .addCapability(openIdAuthenticator)
            .target(IcecreamClient.class, "http://localhost:" + randomServerPort);

    Collection<Mixin> mixins = client.getAvailableMixins();
    assertThat(mixins)
        .isNotNull()
        .isNotEmpty()
        .hasSize(6)
        .containsExactlyInAnyOrder(Mixin.values());
  }

  @Test
  void testClientSecretPost() {
    OpenIdAuthentication openIdAuthenticator =
        OpenIdAuthentication.discover(
            ClientRegistration.builder()
                .credentials(
                    Credentials.builder()
                        .clientId(KeyCloakCredentials.FEIGN_CLIENT_ID_WITH_SECRET)
                        .clientSecret(KeyCloakCredentials.CLIENT_SECRET_CREDENTIALS)
                        .build())
                .providerDetails(ProviderDetails.builder().issuerUri(issuer()).build())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .build());

    IcecreamClient client =
        Feign.builder()
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .addCapability(openIdAuthenticator)
            .target(IcecreamClient.class, "http://localhost:" + randomServerPort);

    Collection<Mixin> mixins = client.getAvailableMixins();
    assertThat(mixins)
        .isNotNull()
        .isNotEmpty()
        .hasSize(6)
        .containsExactlyInAnyOrder(Mixin.values());
  }

  @Test
  void testClientSecretJwt() {
    OpenIdAuthentication openIdAuthenticator =
        OpenIdAuthentication.discover(
            ClientRegistration.builder()
                .credentials(
                    Credentials.builder()
                        .clientId(KeyCloakCredentials.FEIGN_CLIENT_ID_JWT_SIGNED_WITH_SECRET)
                        .clientSecret(KeyCloakCredentials.CLIENT_SECRET_SIGNATURE)
                        .build())
                .providerDetails(ProviderDetails.builder().issuerUri(issuer()).build())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_JWT)
                .build());

    IcecreamClient client =
        Feign.builder()
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .addCapability(openIdAuthenticator)
            .target(IcecreamClient.class, "http://localhost:" + randomServerPort);

    Collection<Mixin> mixins = client.getAvailableMixins();
    assertThat(mixins)
        .isNotNull()
        .isNotEmpty()
        .hasSize(6)
        .containsExactlyInAnyOrder(Mixin.values());
  }

  @Test
  void testPrivateKeyJwt() throws NoSuchAlgorithmException, InvalidKeySpecException {
    byte[] key = Base64.getDecoder().decode(KeyUtils.parseKey(privateKeyPem));
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    PKCS8EncodedKeySpec keySpecPKCS8 = new PKCS8EncodedKeySpec(key);
    PrivateKey privKey = keyFactory.generatePrivate(keySpecPKCS8);
    Algorithm signingAlgorithm = Algorithm.RSA256(null, (RSAPrivateKey) privKey);

    OpenIdAuthentication openIdAuthenticator =
        OpenIdAuthentication.discover(
            ClientRegistration.builder()
                .credentials(
                    Credentials.builder()
                        .clientId(KeyCloakCredentials.FEIGN_CLIENT_ID_PRIVATE_KEY_JWT)
                        .jwtSignatureAlgorithm(signingAlgorithm)
                        .build())
                .providerDetails(ProviderDetails.builder().issuerUri(issuer()).build())
                .clientAuthenticationMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
                .build());

    IcecreamClient client =
        Feign.builder()
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .addCapability(openIdAuthenticator)
            .target(IcecreamClient.class, "http://localhost:" + randomServerPort);

    Collection<Mixin> mixins = client.getAvailableMixins();
    assertThat(mixins)
        .isNotNull()
        .isNotEmpty()
        .hasSize(6)
        .containsExactlyInAnyOrder(Mixin.values());
  }
}
