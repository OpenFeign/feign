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

import static java.lang.String.format;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Origin exception type for all Http Apis.
 */
public class FeignException extends RuntimeException {

  private static final long serialVersionUID = 0;
  private int status;

  protected String body;

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


  public String body() {
    return body;
  }

  static FeignException errorReading(Request request, Response ignored, IOException cause) {
    String message = format("%s reading %s %s", cause.getMessage(), request.method(), request.url());
    
    FeignException exc = new FeignException(message, cause);
    exc.body = fetchRequestBody(request);

    return exc;
  }


  public static FeignException errorStatus(String methodKey, Response response) {
    String message = format("status %s reading %s", response.status(), methodKey);

    FeignException exception = new FeignException(response.status(), message);
    exception.body = fetchResponseBody(response);

    return exception;
  }

  static FeignException errorExecuting(Request request, IOException cause) {
    String message = format("%s executing %s %s", cause.getMessage(), request.method(), request.url());
    
    RetryableException exception = new RetryableException(message, cause,null);
    exception.body = fetchRequestBody(request);

    return exception;
  }

  private static String fetchRequestBody(Request request) {
    if (request.body() != null) {
      try {
        // no way of getting body's charset, assuming UTF-8, other charsets may generate
        // errors, hence the catch and ignore
        String body = new String(request.body(), StandardCharsets.UTF_8);
        return body;
      } catch (Exception e) {
        // ignore, no body available, sorry
      }
    }
    return null;
  }

  private static String fetchResponseBody(Response response) {
    try {
      if (response.body() != null) {
        String body;
        body = Util.toString(response.body().asReader());
        return body;
      }
    } catch (IOException ignored) {
      // ignore, no body available, sorry
    }
    return null;
  }


}
