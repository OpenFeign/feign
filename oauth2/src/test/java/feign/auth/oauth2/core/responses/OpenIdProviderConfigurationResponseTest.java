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
package feign.auth.oauth2.core.responses;

import static feign.auth.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
import static feign.auth.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_POST;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OpenIdProviderConfigurationResponseTest {

  @Test
  void testFromMap() {

    /* Given */
    Map<String, Object> map =
        Map.of(
            "issuer", "https://idp.com",
            "authorization_endpoint", "https://idp.com/oauth/authorize",
            "token_endpoint", "https://idp.com/oauth/token",
            "userinfo_endpoint", "https://idp.com/userinfo",
            "jwks_uri", "https://idp.com/.well-known/jwks.json",
            "token_endpoint_auth_methods_supported",
                Set.of("client_secret_basic", "client_secret_post"),
            "scopes_supported", Set.of("openid", "email", "phone", "profile"));

    /* When */
    OpenIdProviderConfigurationResponse response = OpenIdProviderConfigurationResponse.fromMap(map);

    /* Then */
    assertThat(response)
        .isNotNull()
        .hasFieldOrPropertyWithValue("issuer", "https://idp.com")
        .hasFieldOrPropertyWithValue("authorizationEndpoint", "https://idp.com/oauth/authorize")
        .hasFieldOrPropertyWithValue("tokenEndpoint", "https://idp.com/oauth/token")
        .hasFieldOrPropertyWithValue("userinfoEndpoint", "https://idp.com/userinfo")
        .hasFieldOrPropertyWithValue("jwksUri", "https://idp.com/.well-known/jwks.json")
        .hasFieldOrPropertyWithValue(
            "clientAuthenticationMethods", Set.of(CLIENT_SECRET_BASIC, CLIENT_SECRET_POST))
        .hasFieldOrPropertyWithValue(
            "scopesSupported", Set.of("openid", "email", "phone", "profile"));
  }
}
