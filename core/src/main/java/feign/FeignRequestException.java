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

/**
 * Exception is used for cases involving problems when processing a request.
 * This exception contains a {@link Request} that caused problems.
 */
public class FeignRequestException extends FeignException {

  private static final long serialVersionUID = 0;
  private final Request request;

  protected FeignRequestException(String message, Throwable cause, Request request) {
    super(message, cause);
    this.request = request;
  }

  public Request request() {
    return request;
  }

  public String requestBody() {
    return new String(request.body(), UTF_8);
  }
}
