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

import java.util.Collections;
import java.util.Map;

public final class ProviderDetails {
  private final String authorizationUri;
  private final String tokenUri;
  private final String userInfoUri;
  private final String jwkSetUri;
  private final String issuerUri;
  private final Map<String, Object> configurationMetadata;

  public String getAuthorizationUri() {
    return authorizationUri;
  }

  public Map<String, Object> getConfigurationMetadata() {
    return configurationMetadata;
  }

  public String getIssuerUri() {
    return issuerUri;
  }

  public String getJwkSetUri() {
    return jwkSetUri;
  }

  public String getTokenUri() {
    return tokenUri;
  }

  public String getUserInfoUri() {
    return userInfoUri;
  }

  private ProviderDetails(Builder builder) {
    authorizationUri = builder.authorizationUri;
    tokenUri = builder.tokenUri;
    userInfoUri = builder.userInfoUri;
    jwkSetUri = builder.jwkSetUri;
    issuerUri = builder.issuerUri;
    configurationMetadata = builder.configurationMetadata;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String authorizationUri;
    private String tokenUri;
    private String userInfoUri;
    private String jwkSetUri;
    private String issuerUri;
    private Map<String, Object> configurationMetadata = Collections.emptyMap();

    private Builder() {}

    public Builder authorizationUri(final String authorizationUri) {
      this.authorizationUri = authorizationUri;
      return this;
    }

    public Builder tokenUri(final String tokenUri) {
      this.tokenUri = tokenUri;
      return this;
    }

    public Builder userInfoUri(final String userInfoUri) {
      this.userInfoUri = userInfoUri;
      return this;
    }

    public Builder jwkSetUri(final String jwkSetUri) {
      this.jwkSetUri = jwkSetUri;
      return this;
    }

    public Builder issuerUri(final String issuerUri) {
      this.issuerUri = issuerUri;
      return this;
    }

    public Builder configurationMetadata(final Map<String, Object> configurationMetadata) {
      this.configurationMetadata = configurationMetadata;
      return this;
    }

    public ProviderDetails build() {
      return new ProviderDetails(this);
    }
  }
}
