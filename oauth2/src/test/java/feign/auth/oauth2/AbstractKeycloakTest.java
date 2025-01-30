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

import com.adelean.inject.resources.junit.jupiter.GivenTextResource;
import com.adelean.inject.resources.junit.jupiter.TestWithResources;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import feign.auth.oauth2.support.KeyUtils;
import java.util.Collections;
import org.junit.jupiter.api.BeforeAll;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestWithResources
public abstract class AbstractKeycloakTest extends AbstractAuthenticationTest {

  @GivenTextResource("certificate.pem")
  protected static String certificatePem;

  @GivenTextResource("private_key.pem")
  protected static String privateKeyPem;

  @Container
  protected static KeycloakContainer keycloak =
      new KeycloakContainer("quay.io/keycloak/keycloak:26.1.0");

  @DynamicPropertySource
  static void dynamicProperties(DynamicPropertyRegistry registry) {
    String host = keycloak.getHost();
    int port = keycloak.getHttpPort();

    registry.add(
        "spring.security.oauth2.resourceserver.jwt.issuer-uri",
        () -> String.format("http://%s:%d/realms/master", host, port));
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
        () ->
            String.format("http://%s:%d/realms/master/protocol/openid-connect/certs", host, port));
  }

  @BeforeAll
  static void initKeycloak() {
    ClientRepresentation clientFeignSecret = new ClientRepresentation();
    clientFeignSecret.setClientId(KeyCloakCredentials.FEIGN_CLIENT_ID_WITH_SECRET);
    clientFeignSecret.setSecret(KeyCloakCredentials.CLIENT_SECRET_CREDENTIALS);
    clientFeignSecret.setServiceAccountsEnabled(true);
    clientFeignSecret.setClientAuthenticatorType("client-secret");

    ClientRepresentation clientJwtSignedWithSecret = new ClientRepresentation();
    clientJwtSignedWithSecret.setClientId(
        KeyCloakCredentials.FEIGN_CLIENT_ID_JWT_SIGNED_WITH_SECRET);
    clientJwtSignedWithSecret.setSecret(KeyCloakCredentials.CLIENT_SECRET_SIGNATURE);
    clientJwtSignedWithSecret.setServiceAccountsEnabled(true);
    clientJwtSignedWithSecret.setClientAuthenticatorType("client-secret-jwt");

    ClientRepresentation clientPrivateKeyJwt = new ClientRepresentation();
    clientPrivateKeyJwt.setClientId(KeyCloakCredentials.FEIGN_CLIENT_ID_PRIVATE_KEY_JWT);
    clientPrivateKeyJwt.setServiceAccountsEnabled(true);
    clientPrivateKeyJwt.setClientAuthenticatorType("client-jwt");

    String certificate = KeyUtils.parseKey(certificatePem);
    clientPrivateKeyJwt.setAttributes(
        Collections.singletonMap("jwt.credential.certificate", certificate));

    ClientsResource clientsApi =
        keycloak.getKeycloakAdminClient().realms().realm(KeycloakContainer.MASTER_REALM).clients();
    clientsApi.create(clientFeignSecret);
    clientsApi.create(clientJwtSignedWithSecret);
    clientsApi.create(clientPrivateKeyJwt);
  }

  protected static String keycloakHost() {
    String host = keycloak.getHost();
    int port = keycloak.getHttpPort();
    return String.format("http://%s:%d", host, port);
  }

  protected static String issuer() {
    return String.format("%s/realms/%s", keycloakHost(), KeyCloakCredentials.REALM);
  }
}
