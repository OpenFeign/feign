/*
 * Copyright 2013 Netflix, Inc.
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

import java.io.IOException;

import static java.lang.String.format;

/**
 * Origin exception type for all Http Apis.
 */
public class FeignException extends RuntimeException {

  private static final long serialVersionUID = 0;
  private int status;

  protected FeignException(String message, Throwable cause) {
    super(message, cause);
  }

  protected FeignException(String message) {
    super(message);
  }

  protected FeignException(int status, String message) {
    super(message);
    this.status = status;
  }

  public int status() {
    return this.status;
  }

  static FeignException errorReading(Request request, Response ignored, IOException cause) {
    return new FeignException(
        format("%s reading %s %s", cause.getMessage(), request.method(), request.url()),
        cause);
  }

  public static FeignException errorStatus(String methodKey, Response response) {
    String message = format("status %s reading %s", response.status(), methodKey);
    try {
      if (response.body() != null) {
        String body = Util.toString(response.body().asReader());
        message += "; content:\n" + body;
      }
    } catch (IOException ignored) { // NOPMD
    }
    return new FeignException(response.status(), message);
  }

  static FeignException errorExecuting(Request request, IOException cause) {
    return new RetryableException(
        format("%s executing %s %s", cause.getMessage(), request.method(), request.url()), cause,
        null);
  }
}
