/**
 * Copyright 2012-2018 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign;

import static feign.Util.UTF_8;
import static java.lang.String.format;
import java.io.IOException;

/**
 * Origin exception type for all Http Apis.
 */
public class FeignException extends RuntimeException {

  private static final long serialVersionUID = 0;
  private int status;
  private byte[] content;

  protected FeignException(String message, Throwable cause) {
    super(message, cause);
  }

  protected FeignException(String message, Throwable cause, byte[] content) {
    super(message, cause);
    this.content = content;
  }

  protected FeignException(String message) {
    super(message);
  }

  protected FeignException(int status, String message, byte[] content) {
    super(message);
    this.status = status;
    this.content = content;
  }

  public int status() {
    return this.status;
  }

  public byte[] content() {
    return this.content;
  }

  public String contentUTF8() {
    return new String(content, UTF_8);
  }

  static FeignException errorReading(Request request, Response ignored, IOException cause) {
    return new FeignException(
        format("%s reading %s %s", cause.getMessage(), request.httpMethod(), request.url()),
        cause,
        request.body());
  }

  public static FeignException errorStatus(String methodKey, Response response) {
    String message = format("status %s reading %s", response.status(), methodKey);

    byte[] body = {};
    try {
      if (response.body() != null) {
        body = Util.toByteArray(response.body().asInputStream());
      }
    } catch (IOException ignored) { // NOPMD
    }

    return new FeignException(response.status(), message, body);
  }

  static FeignException errorExecuting(Request request, IOException cause) {
    return new RetryableException(
        format("%s executing %s %s", cause.getMessage(), request.httpMethod(), request.url()),
        request.httpMethod(),
        cause,
        null);
  }
}
