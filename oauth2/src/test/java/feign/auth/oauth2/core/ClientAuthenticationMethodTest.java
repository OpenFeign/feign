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
package feign.auth.oauth2.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClientAuthenticationMethodTest {

  @ParameterizedTest
  @CsvSource({
    "client_secret_basic, CLIENT_SECRET_BASIC",
    "client_secret_post, CLIENT_SECRET_POST",
    "client_secret_jwt, CLIENT_SECRET_JWT",
    "private_key_jwt, PRIVATE_KEY_JWT",
    "tls_client_auth, TLS_CLIENT_AUTH",
    "self_signed_tls_client_auth, SELF_SIGNED_TLS_CLIENT_AUTH",
    "none, NONE",
  })
  void testParse(String str, ClientAuthenticationMethod expected) {

    /* When */
    ClientAuthenticationMethod authenticationMethod = ClientAuthenticationMethod.parse(str);

    /* Then */
    assertThat(authenticationMethod).isNotNull().isEqualTo(expected);
  }
}
