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

import com.auth0.jwt.algorithms.Algorithm;
import java.util.Base64;

public final class Credentials {
  private final String clientId;
  private final String clientSecret;
  private final Algorithm jwtSignatureAlgorithm;

  private Credentials(Builder builder) {
    clientId = builder.clientId;
    clientSecret = builder.clientSecret;
    jwtSignatureAlgorithm = builder.jwtSignatureAlgorithm;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String basicHeader() {
    final String credentials = clientId + ':' + clientSecret;
    return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
  }

  public String getClientId() {
    return clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public Algorithm getJwtSignatureAlgorithm() {
    return jwtSignatureAlgorithm;
  }

  public static class Builder {
    private String clientId;
    private String clientSecret;
    private Algorithm jwtSignatureAlgorithm;

    private Builder() {}

    public Builder clientId(final String clientId) {
      this.clientId = clientId;
      return this;
    }

    public Builder clientSecret(final String clientSecret) {
      this.clientSecret = clientSecret;
      return this;
    }

    public Builder jwtSignatureAlgorithm(final Algorithm jwtSignatureAlgorithm) {
      this.jwtSignatureAlgorithm = jwtSignatureAlgorithm;
      return this;
    }

    public Credentials build() {
      return new Credentials(this);
    }
  }
}
