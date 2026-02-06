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

import static feign.auth.oauth2.support.EnvTestConditions.ENV;
import static org.assertj.core.api.Assertions.assertThat;

import feign.AsyncFeign;
import feign.Feign;
import feign.auth.oauth2.core.registration.ClientRegistration;
import feign.auth.oauth2.core.registration.Credentials;
import feign.auth.oauth2.core.registration.ProviderDetails;
import feign.auth.oauth2.mock.IcecreamClient;
import feign.auth.oauth2.mock.domain.Mixin;
import feign.hc5.AsyncApacheHttp5Client;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import java.util.Collection;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@EnabledIf("feign.auth.oauth2.support.EnvTestConditions#testsWithCognitoEnabled")
public class AWSCognitoAuthenticationTest extends AbstractAuthenticationTest {

  @DynamicPropertySource
  static void dynamicProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ENV.get("AWS_ISSUER_URI"));
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> ENV.get("AWS_JWK_SET_URI"));
  }

  @Test
  void testWithCognito() {
    AWSCognitoAuthentication awsCognitoAuthentication =
        new AWSCognitoAuthentication(
            ENV.get("AWS_DOMAIN"),
            ENV.get("AWS_REGION"),
            ENV.get("AWS_CLIENT_ID"),
            ENV.get("AWS_CLIENT_SECRET"));

    IcecreamClient client =
        AsyncFeign.<HttpClientContext>builder()
            .client(new AsyncApacheHttp5Client())
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .addCapability(awsCognitoAuthentication)
            .target(IcecreamClient.class, "http://localhost:" + randomServerPort);

    Collection<Mixin> mixins = client.getAvailableMixins();
    assertThat(mixins)
        .isNotNull()
        .isNotEmpty()
        .hasSize(6)
        .containsExactlyInAnyOrder(Mixin.values());
  }

  @Test
  void testWithCognitoDiscovery() {
    String issuer =
        String.format(
            "https://cognito-idp.%s.amazonaws.com/%s",
            ENV.get("AWS_REGION"), ENV.get("AWS_TENANT"));
    OpenIdAuthentication openIdAuthenticator =
        OpenIdAuthentication.discover(
            ClientRegistration.builder()
                .credentials(
                    Credentials.builder()
                        .clientId(ENV.get("AWS_CLIENT_ID"))
                        .clientSecret(ENV.get("AWS_CLIENT_SECRET"))
                        .build())
                .providerDetails(ProviderDetails.builder().issuerUri(issuer).build())
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
