/**
 * Copyright 2012-2020 The Feign Authors
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

import static feign.Util.checkNotNull;
import java.util.concurrent.CompletableFuture;

/**
 * Thrown to encapsulate an underlying cause when using {@link CompletableFuture#join()} to convert
 * an asynchronous call to a synchronous one.
 */
@Experimental
public class AsyncJoinException extends FeignException {

  private static final long serialVersionUID = 1L;

  /**
   * @param message possibly null reason for the failure.
   * @param cause the cause of the error.
   */
  public AsyncJoinException(int status, String message, Request request, Throwable cause) {
    super(status, message, request, checkNotNull(cause, "cause"));
  }
}
