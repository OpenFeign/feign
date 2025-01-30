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

import java.util.Map;
import java.util.Optional;

public final class OAuth2TokenResponse {
  private String accessToken;
  private String tokenType;
  private int expiresIn;
  private String refreshToken;
  private String scope;

  public OAuth2TokenResponse(
      final String accessToken,
      final String tokenType,
      final int expiresIn,
      final String refreshToken,
      final String scope) {
    if (accessToken == null || accessToken.isEmpty()) {
      throw new IllegalArgumentException("accessToken cannot be empty");
    }

    if (tokenType == null || tokenType.isEmpty()) {
      throw new IllegalArgumentException("tokenType cannot be empty");
    }

    this.accessToken = accessToken;
    this.tokenType = tokenType;
    this.expiresIn = expiresIn;
    this.refreshToken = refreshToken;
    this.scope = scope;
  }

  public String getAccessToken() {
    return accessToken;
  }

  public String getTokenType() {
    return tokenType;
  }

  public int getExpiresIn() {
    return expiresIn;
  }

  public Optional<String> getRefreshToken() {
    return Optional.ofNullable(refreshToken);
  }

  public Optional<String> getScope() {
    return Optional.ofNullable(scope);
  }

  public static OAuth2TokenResponse fromMap(final Map<String, Object> map) {
    final String accessToken = (String) map.get("access_token");
    final String tokenType = (String) map.get("token_type");
    final int expiresIn = (Integer) map.get("expires_in");
    final String refreshToken = (String) map.get("refresh_token");
    final String scope = (String) map.get("scope");

    return new OAuth2TokenResponse(accessToken, tokenType, expiresIn, refreshToken, scope);
  }
}
