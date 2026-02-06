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

import feign.AsyncClient;
import feign.FeignException;
import feign.Request;
import feign.auth.oauth2.core.ClientAuthenticationMethod;
import feign.auth.oauth2.core.registration.ClientRegistration;
import feign.auth.oauth2.core.responses.OAuth2TokenResponse;
import feign.codec.Decoder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class OAuth2IDPClient {
  protected final AsyncClient<Object> client;
  protected final Request.Options options;
  protected final Decoder jsonDecoder;

  public OAuth2IDPClient(
      final AsyncClient<Object> client, final Request.Options options, final Decoder jsonDecoder) {
    this.client = client;
    this.options = options;
    this.jsonDecoder = jsonDecoder;
  }

  public CompletableFuture<OAuth2TokenResponse> authenticateClient(
      final ClientRegistration clientRegistration) {
    if (clientRegistration.getClientAuthenticationMethod().getFramework() != OAUTH2) {
      throw new IllegalArgumentException(
          String.format(
              "Authentication method %s is not supported by OAuth2 client.",
              clientRegistration.getClientAuthenticationMethod()));
    }

    final Request request = clientSecretRequest(clientRegistration);
    return executeRequest(request);
  }

  protected CompletableFuture<OAuth2TokenResponse> executeRequest(final Request request) {
    return client
        .execute(request, options, Optional.empty())
        .thenApply(
            response -> {
              try {
                if (response.status() >= 200 && response.status() < 300) {
                  @SuppressWarnings("unchecked")
                  final Map<String, Object> jsonResponse =
                      (Map<String, Object>) jsonDecoder.decode(response, Map.class);
                  return OAuth2TokenResponse.fromMap(jsonResponse);
                } else {
                  throw FeignException.errorStatus("authentication", response, 4000, 2000);
                }
              } catch (final IOException networkError) {
                throw new RuntimeException(networkError);
              }
            });
  }

  protected static Request clientSecretRequest(final ClientRegistration clientRegistration) {
    final Map<String, Collection<String>> headers = new HashMap<>();
    headers.put("Content-Type", Collections.singleton("application/x-www-form-urlencoded"));

    if (clientRegistration.getClientAuthenticationMethod()
        == ClientAuthenticationMethod.CLIENT_SECRET_BASIC) {
      headers.put(
          "Authorization",
          Collections.singleton(clientRegistration.getCredentials().basicHeader()));
    }

    String body = "grant_type=client_credentials";

    if (clientRegistration.getAudience() != null && !clientRegistration.getAudience().isEmpty()) {
      body += "&audience=" + clientRegistration.getAudience();
    }

    if (clientRegistration.getClientAuthenticationMethod()
        == ClientAuthenticationMethod.CLIENT_SECRET_POST) {
      body +=
          String.format(
              "&client_id=%s&client_secret=%s",
              clientRegistration.getCredentials().getClientId(),
              clientRegistration.getCredentials().getClientSecret());
    }

    body +=
        clientRegistration.getExtraParameters().entrySet().stream()
            .map(entry -> String.format("&%s=%s", entry.getKey(), entry.getValue()))
            .collect(Collectors.joining());

    return Request.create(
        Request.HttpMethod.POST,
        clientRegistration.getProviderDetails().getTokenUri(),
        headers,
        body.getBytes(),
        StandardCharsets.UTF_8,
        null);
  }
}
