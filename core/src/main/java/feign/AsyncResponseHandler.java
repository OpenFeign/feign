/*
 * Copyright 2012-2022 The Feign Authors
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

import static feign.FeignException.errorReading;
import static feign.Util.ensureClosed;
import feign.Logger.Level;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;

/**
 * The response handler that is used to provide asynchronous support on top of standard response
 * handling
 */
@Experimental
class AsyncResponseHandler {

  private static final long MAX_RESPONSE_BUFFER_SIZE = 8192L;

  private final Level logLevel;
  private final Logger logger;

  private final Decoder decoder;
  private final ErrorDecoder errorDecoder;
  private final boolean dismiss404;
  private final boolean closeAfterDecode;

  private final ResponseInterceptor responseInterceptor;

  AsyncResponseHandler(Level logLevel, Logger logger, Decoder decoder,
      ErrorDecoder errorDecoder, boolean dismiss404, boolean closeAfterDecode,
      ResponseInterceptor responseInterceptor) {
    super();
    this.logLevel = logLevel;
    this.logger = logger;
    this.decoder = decoder;
    this.errorDecoder = errorDecoder;
    this.dismiss404 = dismiss404;
    this.closeAfterDecode = closeAfterDecode;
    this.responseInterceptor = responseInterceptor;
  }

  boolean isVoidType(Type returnType) {
    return Void.class == returnType || void.class == returnType
        || returnType.getTypeName().equals("kotlin.Unit");
  }

  void handleResponse(CompletableFuture<Object> resultFuture,
                      String configKey,
                      Response response,
                      Type returnType,
                      long elapsedTime) {
    // copied fairly liberally from SynchronousMethodHandler
    boolean shouldClose = true;

    try {
      if (logLevel != Level.NONE) {
        response = logger.logAndRebufferResponse(configKey, logLevel, response,
            elapsedTime);
      }
      if (Response.class == returnType) {
        if (response.body() == null) {
          resultFuture.complete(response);
        } else if (response.body().length() == null
            || response.body().length() > MAX_RESPONSE_BUFFER_SIZE) {
          shouldClose = false;
          resultFuture.complete(response);
        } else {
          // Ensure the response body is disconnected
          final byte[] bodyData = Util.toByteArray(response.body().asInputStream());
          resultFuture.complete(response.toBuilder().body(bodyData).build());
        }
      } else if (response.status() >= 200 && response.status() < 300) {
        if (isVoidType(returnType)) {
          resultFuture.complete(null);
        } else {
          final Object result = decode(response, returnType);
          shouldClose = closeAfterDecode;
          resultFuture.complete(result);
        }
      } else if (dismiss404 && response.status() == 404 && !isVoidType(returnType)) {
        final Object result = decode(response, returnType);
        shouldClose = closeAfterDecode;
        resultFuture.complete(result);
      } else {
        resultFuture.completeExceptionally(errorDecoder.decode(configKey, response));
      }
    } catch (final IOException e) {
      if (logLevel != Level.NONE) {
        logger.logIOException(configKey, logLevel, e, elapsedTime);
      }
      resultFuture.completeExceptionally(errorReading(response.request(), response, e));
    } catch (final Exception e) {
      resultFuture.completeExceptionally(e);
    } finally {
      if (shouldClose) {
        ensureClosed(response.body());
      }
    }

  }

  Object decode(Response response, Type type) throws IOException {
    return responseInterceptor.aroundDecode(new InvocationContext(decoder, type, response));
  }
}
