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
package feign;

/**
 * Enumerates the HTTP status codes recognised by Feign's exception hierarchy. Using named constants
 * instead of bare integers removes magic numbers from call sites and keeps the status-to-exception
 * mapping in a single place.
 */
public enum HttpStatus {
  OK(200),
  NO_CONTENT(204),
  RESET_CONTENT(205),
  BAD_REQUEST(400),
  UNAUTHORIZED(401),
  FORBIDDEN(403),
  NOT_FOUND(404),
  METHOD_NOT_ALLOWED(405),
  NOT_ACCEPTABLE(406),
  CONFLICT(409),
  GONE(410),
  UNSUPPORTED_MEDIA_TYPE(415),
  UNPROCESSABLE_ENTITY(422),
  TOO_MANY_REQUESTS(429),
  INTERNAL_SERVER_ERROR(500),
  NOT_IMPLEMENTED(501),
  BAD_GATEWAY(502),
  SERVICE_UNAVAILABLE(503),
  GATEWAY_TIMEOUT(504);

  private final int code;

  HttpStatus(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }

  /**
   * Resolves an integer status code to the matching {@link HttpStatus} constant, or {@code null} if
   * the code is not enumerated here.
   */
  public static HttpStatus from(int code) {
    for (HttpStatus status : values()) {
      if (status.code == code) {
        return status;
      }
    }
    return null;
  }

  /** {@code true} when the status indicates a 4xx client error. */
  public boolean isClientError() {
    return code >= 400 && code < 500;
  }

  /** {@code true} when the status indicates a 5xx server error. */
  public boolean isServerError() {
    return code >= 500 && code <= 599;
  }
}
