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

import static java.lang.String.format;

import feign.codec.StringDecoder;
import java.io.IOException;

/** Origin exception type for all Http Apis. */
public class FeignException extends RuntimeException {
  static FeignException errorReading(Request request, Response response, IOException cause) {
    return new FeignException(
        format("%s %s %s", cause.getMessage(), request.method(), request.url(), 0), cause);
  }

  private static final StringDecoder toString = new StringDecoder();

  public static FeignException errorStatus(String methodKey, Response response) {
    String message = format("status %s reading %s", response.status(), methodKey);
    try {
      Object body = toString.decode(methodKey, response, String.class);
      if (body != null) {
        response =
            Response.create(
                response.status(), response.reason(), response.headers(), body.toString());
        message += "; content:\n" + body;
      }
    } catch (IOException ignored) { // NOPMD
    }
    return new FeignException(message);
  }

  static FeignException errorExecuting(Request request, IOException cause) {
    return new RetryableException(
        format("error %s executing %s %s", cause.getMessage(), request.method(), request.url()),
        cause,
        null);
  }

  protected FeignException(String message, Throwable cause) {
    super(message, cause);
  }

  protected FeignException(String message) {
    super(message);
  }

  private static final long serialVersionUID = 0;
}
