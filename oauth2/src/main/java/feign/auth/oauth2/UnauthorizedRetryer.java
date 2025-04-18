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

import feign.RequestTemplate;
import feign.RetryableException;
import feign.Retryer;
import java.util.logging.Level;

final class UnauthorizedRetryer implements Retryer {
  private static final java.util.logging.Logger JAVA_LOGGER =
      java.util.logging.Logger.getLogger(UnauthorizedRetryer.class.getName());

  private final OAuth2Authentication authentication;
  private final Retryer optionalDelegate;
  private boolean reauthenticated = false;

  UnauthorizedRetryer(
      final OAuth2Authentication authentication, /* Nullable */ final Retryer optionalDelegate) {
    this.authentication = authentication;
    this.optionalDelegate = optionalDelegate;
  }

  @Override
  public void continueOrPropagate(final RetryableException unauthorizedException) {
    if (unauthorizedException.status() != 401) {
      if (optionalDelegate != null) {
        optionalDelegate.continueOrPropagate(unauthorizedException);
      } else {
        throw unauthorizedException;
      }
    }

    if (reauthenticated) {
      JAVA_LOGGER.log(
          Level.WARNING,
          "Client still unauthorized event after access token was updated. Fail request.");
      if (optionalDelegate != null) {
        optionalDelegate.continueOrPropagate(unauthorizedException);
      } else {
        throw unauthorizedException;
      }
    }

    JAVA_LOGGER.log(
        Level.INFO, "Request was unauthorized by Resource Server. Refresh access token.");
    final String accessToken = authentication.forceAuthentication();

    final RequestTemplate requestTemplate = unauthorizedException.request().requestTemplate();
    requestTemplate.removeHeader("Authorization");
    requestTemplate.header("Authorization", "Bearer " + accessToken);

    reauthenticated = true;
  }

  @Override
  public Retryer clone() {
    return new UnauthorizedRetryer(
        authentication, optionalDelegate != null ? optionalDelegate.clone() : null);
  }
}
