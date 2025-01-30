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
package feign.auth.oauth2.core.clients;

import static feign.auth.oauth2.core.AuthenticationFramework.OAUTH2;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import feign.AsyncClient;
import feign.FeignException;
import feign.Request;
import feign.Util;
import feign.auth.oauth2.core.ClientAuthenticationMethod;
import feign.auth.oauth2.core.registration.ClientRegistration;
import feign.auth.oauth2.core.registration.ProviderDetails;
import feign.auth.oauth2.core.responses.OAuth2TokenResponse;
import feign.auth.oauth2.core.responses.OpenIdProviderConfigurationResponse;
import feign.codec.Decoder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class OpenIdProviderClient extends OAuth2IDPClient {
  private static final String CONFIGURATION_PATH = "/.well-known/openid-configuration";

  public OpenIdProviderClient(
      final AsyncClient<Object> client, final Request.Options options, final Decoder jsonDecoder) {
    super(client, options, jsonDecoder);
  }

  public CompletableFuture<ClientRegistration> discover(
      final ClientRegistration clientRegistration) {
    final Request request =
        Request.create(
            Request.HttpMethod.GET,
            clientRegistration.getProviderDetails().getIssuerUri() + CONFIGURATION_PATH,
            Collections.emptyMap(),
            null,
            Util.UTF_8,
            null);

    return client
        .execute(request, options, Optional.empty())
        .thenApply(
            response -> {
              try {
                if (response.status() >= 200 && response.status() < 300) {
                  @SuppressWarnings("unchecked")
                  final Map<String, Object> jsonResponse =
                      (Map<String, Object>) jsonDecoder.decode(response, Map.class);
                  OpenIdProviderConfigurationResponse configuration =
                      OpenIdProviderConfigurationResponse.fromMap(jsonResponse);
                  return toClientRegistration(clientRegistration, configuration);
                } else {
                  final byte[] bytes = Util.toByteArray(response.body().asInputStream());
                  final String errMessage = new String(bytes, StandardCharsets.UTF_8);
                  throw new FeignException.FeignClientException(
                      response.status(),
                      errMessage,
                      request,
                      new byte[] {},
                      Collections.emptyMap());
                }
              } catch (final IOException networkError) {
                throw new RuntimeException(networkError);
              }
            });
  }

  @Override
  public CompletableFuture<OAuth2TokenResponse> authenticateClient(
      final ClientRegistration clientRegistration) {
    if (clientRegistration.getClientAuthenticationMethod().getFramework() == OAUTH2) {
      return super.authenticateClient(clientRegistration);
    }

    Request request;

    switch (clientRegistration.getClientAuthenticationMethod()) {
      case CLIENT_SECRET_JWT:
        request = clientSecretJwt(clientRegistration);
        break;
      case PRIVATE_KEY_JWT:
        request = privateKeyJwtRequest(clientRegistration);
        break;
      default:
        throw new UnsupportedOperationException(
            String.format(
                "Authentication method %s is not yet supported.",
                clientRegistration.getClientAuthenticationMethod()));
    }

    return executeRequest(request);
  }

  private static ClientRegistration toClientRegistration(
      final ClientRegistration clientRegistration,
      final OpenIdProviderConfigurationResponse openIdConfiguration) {
    ClientAuthenticationMethod authenticationMethod =
        clientRegistration.getClientAuthenticationMethod();
    if (authenticationMethod == null) {
      authenticationMethod =
          openIdConfiguration.getClientAuthenticationMethods().stream()
              .max(Comparator.comparing(ClientAuthenticationMethod::getValue))
              .orElse(ClientAuthenticationMethod.NONE);
    }

    return ClientRegistration.builder()
        .credentials(clientRegistration.getCredentials())
        .audience(clientRegistration.getAudience())
        .scopes(openIdConfiguration.getScopesSupported())
        .clientAuthenticationMethod(authenticationMethod)
        .extraParameters(clientRegistration.getExtraParameters())
        .providerDetails(
            ProviderDetails.builder()
                .authorizationUri(openIdConfiguration.getAuthorizationEndpoint())
                .tokenUri(openIdConfiguration.getTokenEndpoint())
                .userInfoUri(openIdConfiguration.getUserinfoEndpoint())
                .jwkSetUri(openIdConfiguration.getJwksUri())
                .issuerUri(openIdConfiguration.getIssuer())
                .build())
        .build();
  }

  private static Request clientSecretJwt(final ClientRegistration clientRegistration) {
    if (clientRegistration.getCredentials().getClientSecret() == null) {
      throw new IllegalArgumentException(
          "No client secret provided. "
              + "Client secret is required when client_secret_jwt authentication is used.");
    }

    final Algorithm signingAlgorithm =
        Algorithm.HMAC256(clientRegistration.getCredentials().getClientSecret());
    return clientJwtRequest(clientRegistration, signingAlgorithm);
  }

  private static Request privateKeyJwtRequest(final ClientRegistration clientRegistration) {
    if (clientRegistration.getCredentials().getJwtSignatureAlgorithm() == null) {
      throw new IllegalArgumentException(
          "No signature algorithm provided. "
              + "Signature algorithm is required when private_key_jwt authentication is used.");
    }

    return clientJwtRequest(
        clientRegistration, clientRegistration.getCredentials().getJwtSignatureAlgorithm());
  }

  private static Request clientJwtRequest(
      final ClientRegistration clientRegistration, final Algorithm signingAlgorithm) {
    final Map<String, Collection<String>> headers = new HashMap<>();
    headers.put("Content-Type", Collections.singleton("application/x-www-form-urlencoded"));

    final String jwt = createJwt(clientRegistration).sign(signingAlgorithm);
    final String body =
        "grant_type=client_credentials"
            + "&client_id="
            + clientRegistration.getCredentials().getClientId()
            + "&audience="
            + clientRegistration.getProviderDetails().getTokenUri()
            + "&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
            + "&client_assertion="
            + jwt;

    return Request.create(
        Request.HttpMethod.POST,
        clientRegistration.getProviderDetails().getTokenUri(),
        headers,
        body.getBytes(),
        StandardCharsets.UTF_8,
        null);
  }

  private static JWTCreator.Builder createJwt(final ClientRegistration clientRegistration) {
    return JWT.create()
        .withIssuer(clientRegistration.getCredentials().getClientId())
        .withSubject(clientRegistration.getCredentials().getClientId())
        .withAudience(clientRegistration.getProviderDetails().getTokenUri())
        .withIssuedAt(new Date())
        .withExpiresAt(new Date(System.currentTimeMillis() + 60 * 1000)) // 1 minute expiration
        .withJWTId(UUID.randomUUID().toString()); // Unique identifier
  }
}
