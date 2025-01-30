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
package feign.auth.oauth2.core.registration;

import feign.auth.oauth2.core.AuthorizationGrantType;
import feign.auth.oauth2.core.ClientAuthenticationMethod;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * A representation of a client registration with an OAuth 2.0 or OpenID Connect 1.0 Provider.
 *
 * @author Alexei KLENIN
 * @see <a target="_blank" href="https://tools.ietf.org/html/rfc6749#section-2">Section 2 Client
 *     Registration</a>
 */
public final class ClientRegistration {
  private final String registrationId;
  private final Credentials credentials;
  private final String audience;
  private final ClientAuthenticationMethod clientAuthenticationMethod;
  private final AuthorizationGrantType authorizationGrantType;
  private final String redirectUri;
  private final Set<String> scopes;
  private final ProviderDetails providerDetails;
  private final String clientName;
  private final Map<String, String> extraParameters;

  public AuthorizationGrantType getAuthorizationGrantType() {
    return authorizationGrantType;
  }

  public ClientAuthenticationMethod getClientAuthenticationMethod() {
    return clientAuthenticationMethod;
  }

  public Credentials getCredentials() {
    return credentials;
  }

  public String getClientName() {
    return clientName;
  }

  public String getAudience() {
    return audience;
  }

  public ProviderDetails getProviderDetails() {
    return providerDetails;
  }

  public String getRedirectUri() {
    return redirectUri;
  }

  public String getRegistrationId() {
    return registrationId;
  }

  public Set<String> getScopes() {
    return scopes;
  }

  public Map<String, String> getExtraParameters() {
    return extraParameters;
  }

  private ClientRegistration(Builder builder) {
    registrationId = builder.registrationId;
    credentials = builder.credentials;
    audience = builder.audience;
    clientAuthenticationMethod = builder.clientAuthenticationMethod;
    authorizationGrantType = builder.authorizationGrantType;
    redirectUri = builder.redirectUri;
    scopes = builder.scopes;
    providerDetails = builder.providerDetails;
    clientName = builder.clientName;
    extraParameters = builder.extraParameters;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String registrationId;
    private Credentials credentials;
    private String audience;
    private ClientAuthenticationMethod clientAuthenticationMethod;
    private AuthorizationGrantType authorizationGrantType;
    private String redirectUri;
    private Set<String> scopes = Collections.emptySet();
    private ProviderDetails providerDetails;
    private String clientName;
    private Map<String, String> extraParameters = Collections.emptyMap();

    private Builder() {}

    public Builder registrationId(final String registrationId) {
      this.registrationId = registrationId;
      return this;
    }

    public Builder credentials(final Credentials credentials) {
      this.credentials = credentials;
      return this;
    }

    public Builder audience(final String audience) {
      this.audience = audience;
      return this;
    }

    public Builder clientAuthenticationMethod(
        final ClientAuthenticationMethod clientAuthenticationMethod) {
      this.clientAuthenticationMethod = clientAuthenticationMethod;
      return this;
    }

    public Builder authorizationGrantType(final AuthorizationGrantType authorizationGrantType) {
      this.authorizationGrantType = authorizationGrantType;
      return this;
    }

    public Builder redirectUri(final String redirectUri) {
      this.redirectUri = redirectUri;
      return this;
    }

    public Builder scopes(final Set<String> scopes) {
      this.scopes = scopes;
      return this;
    }

    public Builder providerDetails(final ProviderDetails providerDetails) {
      this.providerDetails = providerDetails;
      return this;
    }

    public Builder clientName(final String clientName) {
      this.clientName = clientName;
      return this;
    }

    public Builder extraParameters(final Map<String, String> extraParameters) {
      this.extraParameters = extraParameters;
      return this;
    }

    public ClientRegistration build() {
      return new ClientRegistration(this);
    }
  }
}
