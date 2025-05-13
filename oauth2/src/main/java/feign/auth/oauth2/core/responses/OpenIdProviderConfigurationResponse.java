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

import feign.auth.oauth2.core.ClientAuthenticationMethod;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The response from OIDC Discovery endpoint.
 *
 * @author Alexei KLENIN
 * @see <a target="_blank"
 *     href="https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderConfigurationResponse">Section
 *     4.2. OpenID Provider Configuration Response</a>
 */
public final class OpenIdProviderConfigurationResponse {
  private final String issuer;
  private final String authorizationEndpoint;
  private final String tokenEndpoint;
  private final String userinfoEndpoint;
  private final Set<ClientAuthenticationMethod> clientAuthenticationMethods;
  private final String jwksUri;
  private final Set<String> scopesSupported;

  private OpenIdProviderConfigurationResponse(Builder builder) {
    issuer = builder.issuer;
    authorizationEndpoint = builder.authorizationEndpoint;
    tokenEndpoint = builder.tokenEndpoint;
    userinfoEndpoint = builder.userinfoEndpoint;
    clientAuthenticationMethods = builder.clientAuthenticationMethods;
    jwksUri = builder.jwksUri;
    scopesSupported = builder.scopesSupported;
  }

  public String getAuthorizationEndpoint() {
    return authorizationEndpoint;
  }

  public Set<ClientAuthenticationMethod> getClientAuthenticationMethods() {
    return clientAuthenticationMethods;
  }

  public String getIssuer() {
    return issuer;
  }

  public String getJwksUri() {
    return jwksUri;
  }

  public Set<String> getScopesSupported() {
    return scopesSupported;
  }

  public String getTokenEndpoint() {
    return tokenEndpoint;
  }

  public String getUserinfoEndpoint() {
    return userinfoEndpoint;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static OpenIdProviderConfigurationResponse fromMap(final Map<String, Object> map) {
    final String issuer = (String) map.get("issuer");
    final String authorizationEndpoint = (String) map.get("authorization_endpoint");
    final String tokenEndpoint = (String) map.get("token_endpoint");
    final String userinfoEndpoint = (String) map.get("userinfo_endpoint");
    final String jwksUri = (String) map.get("jwks_uri");

    final Set<ClientAuthenticationMethod> clientAuthenticationMethods =
        ((Collection<String>) map.get("token_endpoint_auth_methods_supported"))
            .stream().map(ClientAuthenticationMethod::parse).collect(Collectors.toSet());

    final Set<String> scopesSupported =
        new HashSet<>((Collection<String>) map.get("scopes_supported"));

    return OpenIdProviderConfigurationResponse.builder()
        .issuer(issuer)
        .authorizationEndpoint(authorizationEndpoint)
        .tokenEndpoint(tokenEndpoint)
        .userinfoEndpoint(userinfoEndpoint)
        .jwksUri(jwksUri)
        .clientAuthenticationMethods(clientAuthenticationMethods)
        .scopesSupported(scopesSupported)
        .build();
  }

  public static class Builder {
    private String issuer;
    private String authorizationEndpoint;
    private String tokenEndpoint;
    private String userinfoEndpoint;
    private Set<ClientAuthenticationMethod> clientAuthenticationMethods = Collections.emptySet();
    private String jwksUri;
    private Set<String> scopesSupported = Collections.emptySet();

    private Builder() {}

    public Builder issuer(final String issuer) {
      this.issuer = issuer;
      return this;
    }

    public Builder authorizationEndpoint(final String authorizationEndpoint) {
      this.authorizationEndpoint = authorizationEndpoint;
      return this;
    }

    public Builder tokenEndpoint(final String tokenEndpoint) {
      this.tokenEndpoint = tokenEndpoint;
      return this;
    }

    public Builder userinfoEndpoint(final String userinfoEndpoint) {
      this.userinfoEndpoint = userinfoEndpoint;
      return this;
    }

    public Builder clientAuthenticationMethods(
        final Set<ClientAuthenticationMethod> clientAuthenticationMethods) {
      this.clientAuthenticationMethods = clientAuthenticationMethods;
      return this;
    }

    public Builder jwksUri(final String jwksUri) {
      this.jwksUri = jwksUri;
      return this;
    }

    public Builder scopesSupported(final Set<String> scopesSupported) {
      this.scopesSupported = scopesSupported;
      return this;
    }

    public OpenIdProviderConfigurationResponse build() {
      return new OpenIdProviderConfigurationResponse(this);
    }
  }
}
