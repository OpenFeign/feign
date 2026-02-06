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

import feign.Feign;
import feign.auth.oauth2.mock.IcecreamClient;
import feign.auth.oauth2.mock.domain.Mixin;
import feign.auth.oauth2.support.KeyUtils;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import java.util.Collection;
import org.junit.jupiter.api.Test;

public class KeycloakAuthenticationTest extends AbstractKeycloakTest {

  @Test
  void testClientSecretBasic() {
    KeycloakAuthentication keycloakAuthentication =
        KeycloakAuthentication.withClientSecretBasic(
            keycloakHost(),
            KeyCloakCredentials.REALM,
            KeyCloakCredentials.FEIGN_CLIENT_ID_WITH_SECRET,
            KeyCloakCredentials.CLIENT_SECRET_CREDENTIALS);

    IcecreamClient client =
        Feign.builder()
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .addCapability(keycloakAuthentication)
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
    KeycloakAuthentication keycloakAuthentication =
        KeycloakAuthentication.withClientSecretPost(
            keycloakHost(),
            KeyCloakCredentials.REALM,
            KeyCloakCredentials.FEIGN_CLIENT_ID_WITH_SECRET,
            KeyCloakCredentials.CLIENT_SECRET_CREDENTIALS);

    IcecreamClient client =
        Feign.builder()
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .addCapability(keycloakAuthentication)
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
    KeycloakAuthentication keycloakAuthentication =
        KeycloakAuthentication.withClientSecretJwt(
            keycloakHost(),
            KeyCloakCredentials.REALM,
            KeyCloakCredentials.FEIGN_CLIENT_ID_JWT_SIGNED_WITH_SECRET,
            KeyCloakCredentials.CLIENT_SECRET_SIGNATURE);

    IcecreamClient client =
        Feign.builder()
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .addCapability(keycloakAuthentication)
            .target(IcecreamClient.class, "http://localhost:" + randomServerPort);

    Collection<Mixin> mixins = client.getAvailableMixins();
    assertThat(mixins)
        .isNotNull()
        .isNotEmpty()
        .hasSize(6)
        .containsExactlyInAnyOrder(Mixin.values());
  }

  @Test
  void testPrivateKeyJwt() {
    KeycloakAuthentication keycloakAuthentication =
        KeycloakAuthentication.withPrivateKeyJwt(
            keycloakHost(),
            KeyCloakCredentials.REALM,
            KeyCloakCredentials.FEIGN_CLIENT_ID_PRIVATE_KEY_JWT,
            KeyUtils.parseKey(privateKeyPem));

    IcecreamClient client =
        Feign.builder()
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .addCapability(keycloakAuthentication)
            .target(IcecreamClient.class, "http://localhost:" + randomServerPort);

    Collection<Mixin> mixins = client.getAvailableMixins();
    assertThat(mixins)
        .isNotNull()
        .isNotEmpty()
        .hasSize(6)
        .containsExactlyInAnyOrder(Mixin.values());
  }
}
