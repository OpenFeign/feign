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

import static feign.auth.oauth2.core.AuthenticationFramework.*;

import java.util.stream.Stream;

/**
 * The authentication method used when authenticating the client with the authorization server in
 * OAuth2 and OIDC frameworks.
 *
 * @author Alexei KLENIN
 * @see <a target="_blank" href="https://tools.ietf.org/html/rfc6749#section-2.3">Section 2.3 Client
 *     Authentication</a>
 * @see <a
 *     target="https://openid.net/specs/openid-connect-core-1_0.html?utm_source=pocket_saves#ClientAuthentication">Section
 *     9 Client Authentication</a>
 * @see <a target="https://datatracker.ietf.org/doc/html/rfc8705">RFC 8705 - OAuth 2.0 Mutual TLS
 *     Client Authentication and Certificate-Bound Access Tokens.</a>
 */
public enum ClientAuthenticationMethod {
  CLIENT_SECRET_BASIC(OAUTH2, "client_secret_basic", 2),
  CLIENT_SECRET_POST(OAUTH2, "client_secret_post", 1),
  CLIENT_SECRET_JWT(OIDC, "client_secret_jwt", 3),
  PRIVATE_KEY_JWT(OIDC, "private_key_jwt", 4),
  TLS_CLIENT_AUTH(OAUTH2_RFC8705, "tls_client_auth", 6),
  SELF_SIGNED_TLS_CLIENT_AUTH(OAUTH2_RFC8705, "self_signed_tls_client_auth", 5),
  NONE(OIDC, "none", 0);

  private final AuthenticationFramework framework;
  private final String value;
  private final int securityLevel; // the higher, the better

  public AuthenticationFramework getFramework() {
    return framework;
  }

  public int getSecurityLevel() {
    return securityLevel;
  }

  public String getValue() {
    return value;
  }

  ClientAuthenticationMethod(
      final AuthenticationFramework framework, final String value, final int securityLevel) {
    this.framework = framework;
    this.value = value;
    this.securityLevel = securityLevel;
  }

  public static ClientAuthenticationMethod parse(final String str) {
    if (str == null || str.isEmpty()) {
      throw new IllegalArgumentException("value cannot be empty");
    }

    return Stream.of(values())
        .filter(value -> value.value.equals(str))
        .findAny()
        .orElseThrow(
            () -> new IllegalArgumentException(String.format("Unsupported value %s", str)));
  }
}
