/*
 * Copyright 2012-2023 The Feign Authors
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

import feign.Logger.Level;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;

/**
 * The response handler that is used to provide asynchronous support on top of standard response
 * handling
 */
@Experimental
class AsyncResponseHandler {
  private final ResponseHandler responseHandler;

  AsyncResponseHandler(Level logLevel, Logger logger, Decoder decoder,
      ErrorDecoder errorDecoder, boolean dismiss404, boolean closeAfterDecode,
      ResponseInterceptor responseInterceptor) {
    this.responseHandler = new ResponseHandler(
        logLevel, logger, decoder,
        errorDecoder, dismiss404, closeAfterDecode,
        responseInterceptor);
  }

  public CompletableFuture<Object> handleResponse(String configKey,
                                                  Response response,
                                                  Type returnType,
                                                  long elapsedTime) {
    CompletableFuture<Object> resultFuture = new CompletableFuture<>();
    handleResponse(resultFuture, configKey, response, returnType, elapsedTime);
    return resultFuture;
  }

  /**
   * @deprecated use {@link #handleResponse(String, Response, Type, long)} instead.
   */
  @Deprecated()
  void handleResponse(CompletableFuture<Object> resultFuture,
                      String configKey,
                      Response response,
                      Type returnType,
                      long elapsedTime) {
    try {
      resultFuture.complete(
          this.responseHandler.handleResponse(configKey, response, returnType, elapsedTime));
    } catch (Exception e) {
      resultFuture.completeExceptionally(e);
    }
  }
}
