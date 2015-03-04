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
package feign.codec;

import feign.FeignException;

import static feign.Util.checkNotNull;

/**
 * Similar to {@code javax.websocket.EncodeException}, raised when a problem occurs encoding a
 * message.  Note that {@code EncodeException} is not an {@code IOException}, nor does it have one
 * set as its cause.
 */
public class EncodeException extends FeignException {

  private static final long serialVersionUID = 1L;

  /**
   * @param message the reason for the failure.
   */
  public EncodeException(String message) {
    super(checkNotNull(message, "message"));
  }

  /**
   * @param message possibly null reason for the failure.
   * @param cause   the cause of the error.
   */
  public EncodeException(String message, Throwable cause) {
    super(message, checkNotNull(cause, "cause"));
  }
}
