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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class OAuth2TokenResponseTest {

  @Test
  void testFromMap() {

    /* Given */
    Map<String, Object> map =
        Map.of(
            "access_token", "<access token>",
            "token_type", "Bearer",
            "expires_in", 3600,
            "refresh_token", "<refresh token>",
            "scope", "openid");

    /* When */
    OAuth2TokenResponse token = OAuth2TokenResponse.fromMap(map);

    /* Then */
    assertThat(token)
        .isNotNull()
        .hasFieldOrPropertyWithValue("accessToken", "<access token>")
        .hasFieldOrPropertyWithValue("tokenType", "Bearer")
        .hasFieldOrPropertyWithValue("expiresIn", 3600)
        .hasFieldOrPropertyWithValue("refreshToken", Optional.of("<refresh token>"))
        .hasFieldOrPropertyWithValue("scope", Optional.of("openid"));
  }
}
