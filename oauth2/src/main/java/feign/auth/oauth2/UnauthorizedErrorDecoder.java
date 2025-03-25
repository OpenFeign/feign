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

import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;

final class UnauthorizedErrorDecoder implements ErrorDecoder {
  private final ErrorDecoder delegate;

  UnauthorizedErrorDecoder(ErrorDecoder delegate) {
    this.delegate = delegate;
  }

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

    return delegate.decode(methodKey, response);
  }
}
