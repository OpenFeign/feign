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
package feign.auth.oauth2;

import feign.*;
import feign.auth.oauth2.core.clients.OAuth2IDPClient;
import feign.auth.oauth2.core.registration.ClientRegistration;
import feign.auth.oauth2.core.responses.OAuth2TokenResponse;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class OAuth2Authentication implements Capability {
  protected ClientRegistration clientRegistration;
  protected OAuth2IDPClient idpClient;

  protected AsyncClient<Object> httpClient;
  protected Request.Options httpOptions;
  protected Decoder jsonDecoder;

  private OAuth2TokenResponse oAuth2TokenResponse = null;
  private Instant expiresAt = null;

  protected OAuth2Authentication(final ClientRegistration clientRegistration) {
    this.clientRegistration = clientRegistration;
  }

  @Override
  public Client enrich(final Client client) {
    this.httpClient = new AsyncClient.Pseudo<>(client);
    return client;
  }

  @Override
  public AsyncClient<Object> enrich(final AsyncClient<Object> client) {
    this.httpClient = client;
    return client;
  }

  @Override
  public Request.Options enrich(final Request.Options options) {
    this.httpOptions = options;
    return options;
  }

  @Override
  public Decoder enrich(final Decoder decoder) {
    this.jsonDecoder = decoder;
    return decoder;
  }

  @Override
  public <B extends BaseBuilder<B, T>, T> B beforeBuild(final B baseBuilder) {
    if (httpClient == null) {
      throw new IllegalStateException("httpClient is missing");
    }

    if (httpOptions == null) {
      throw new IllegalStateException("httpOptions is missing");
    }

    if (jsonDecoder == null) {
      throw new IllegalStateException("jsonDecoder is missing");
    }

    idpClient = new OAuth2IDPClient(httpClient, httpOptions, jsonDecoder);

    return baseBuilder
        .requestInterceptor(
            (final RequestTemplate requestTemplate) -> {
              final String accessToken = getAccessToken();
              requestTemplate.header("Authorization", "Bearer " + accessToken);
            })
        .retryer(new UnauthorizedRetryer())
        .errorDecoder(UnauthorizedErrorDecoder.INSTANCE);
  }

  private synchronized String getAccessToken() {
    if (expiresAt != null && expiresAt.minus(10, ChronoUnit.SECONDS).isBefore(Instant.now())) {
      // Access token is expired or about to expire
      expiresAt = null;
      oAuth2TokenResponse = null;
    }

    if (oAuth2TokenResponse == null) {
      return forceAuthentication();
    }

    return oAuth2TokenResponse.getAccessToken();
  }

  private synchronized String forceAuthentication() {
    try {
      oAuth2TokenResponse =
          idpClient
              .authenticateClient(clientRegistration)
              .get(httpOptions.readTimeout(), httpOptions.readTimeoutUnit());
    } catch (final InterruptedException | ExecutionException | TimeoutException authException) {
      throw new RuntimeException(authException);
    }

    expiresAt = Instant.now().plus(oAuth2TokenResponse.getExpiresIn(), ChronoUnit.SECONDS);
    return oAuth2TokenResponse.getAccessToken();
  }

  final class UnauthorizedRetryer implements Retryer {
    private boolean reauthenticated = false;

    private UnauthorizedRetryer() {}

    @Override
    public void continueOrPropagate(final RetryableException unauthorizedException) {
      if (unauthorizedException.status() != 401) {
        throw unauthorizedException;
      }

      if (reauthenticated) {
        throw unauthorizedException;
      }

      final String accessToken = forceAuthentication();

      final RequestTemplate requestTemplate = unauthorizedException.request().requestTemplate();
      requestTemplate.removeHeader("Authorization");
      requestTemplate.header("Authorization", "Bearer " + accessToken);

      reauthenticated = true;
    }

    @Override
    public Retryer clone() {
      return new UnauthorizedRetryer();
    }
  }

  static final class UnauthorizedErrorDecoder implements ErrorDecoder {
    private static final UnauthorizedErrorDecoder INSTANCE = new UnauthorizedErrorDecoder();
    private static final ErrorDecoder DEFAULT_ERROR_DECODER = new Default();

    @Override
    public Exception decode(final String methodKey, final Response response) {
      // wrapper 401 to RetryableException in order to retry
      if (response.status() == 401) {
        return new RetryableException(
            response.status(),
            response.reason(),
            response.request().httpMethod(),
            (Long) null,
            response.request());
      }

      return DEFAULT_ERROR_DECODER.decode(methodKey, response);
    }

    private UnauthorizedErrorDecoder() {}
  }
}
