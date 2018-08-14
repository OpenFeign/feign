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

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Exception is used for cases involving problems when processing a response.
 * This exception contains a {@link Response} that caused problems.
 */
public class FeignResponseException extends FeignException {

  private static final long serialVersionUID = 0;
  private final Response response;

  protected FeignResponseException(int status, String message, Response response) {
    super(status, message);

    byte[] responseBytes = {};
    Integer responseLength = 0;
    if (response.body() != null) {
      try {
        responseBytes = Util.toByteArray(response.body().asInputStream());
        responseLength = response.body().length();
      } catch (IOException e) {// NOPMD
      }
    }

    this.response = Response.builder()
        .status(response.status())
        .reason(response.reason())
        .headers(response.headers())
         //Copy response body to new InputStream because root stream is closed already when exception handling
        .body(new ByteArrayInputStream(responseBytes), responseLength)
        .request(response.request())
        .build();
  }

  public Response response() {
    return response;
  }

  public String responseBody() {
    try {
      return Util.toString(response.body().asReader());
    } catch (IOException e) {
      return "Failed to parse response body" + e.getMessage();
    }
  }
}
