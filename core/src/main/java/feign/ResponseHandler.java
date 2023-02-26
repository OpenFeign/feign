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
import java.io.IOException;
import java.lang.reflect.Type;
import static feign.FeignException.errorReading;
import static feign.Util.ensureClosed;

/**
 * The response handler that is used to provide synchronous support on top of standard response
 * handling
 */
public class ResponseHandler {

  private static final long MAX_RESPONSE_BUFFER_SIZE = 8192L;

  private final Level logLevel;
  private final Logger logger;

  private final Decoder decoder;
  private final ErrorDecoder errorDecoder;
  private final boolean dismiss404;
  private final boolean closeAfterDecode;

  private final ResponseInterceptor responseInterceptor;

  public ResponseHandler(Level logLevel, Logger logger, Decoder decoder,
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

  public Object handleResponse(String configKey,
                               Response response,
                               Type returnType,
                               long elapsedTime)
      throws Exception {
    try {
      response = logAndRebufferResponseIfNeeded(configKey, response, elapsedTime);
      if (returnType == Response.class) {
        return disconnectResponseBodyIfNeeded(response);
      }

      final boolean shouldDecodeResponseBody = (response.status() >= 200 && response.status() < 300)
          || (response.status() == 404 && dismiss404 && !isVoidType(returnType));

      if (!shouldDecodeResponseBody) {
        throw decodeError(configKey, response);
      }

      return decode(response, returnType);
    } catch (final IOException e) {
      if (logLevel != Level.NONE) {
        logger.logIOException(configKey, logLevel, e, elapsedTime);
      }
      throw errorReading(response.request(), response, e);
    }
  }

  private boolean isVoidType(Type returnType) {
    return returnType == Void.class
        || returnType == void.class
        || returnType.getTypeName().equals("kotlin.Unit");
  }

  private Response logAndRebufferResponseIfNeeded(String configKey,
                                                  Response response,
                                                  long elapsedTime)
      throws IOException {
    if (logLevel == Level.NONE) {
      return response;
    }

    return logger.logAndRebufferResponse(configKey, logLevel, response, elapsedTime);
  }

  private static Response disconnectResponseBodyIfNeeded(Response response) throws IOException {
    final boolean shouldDisconnectResponseBody = response.body() != null
        && response.body().length() != null
        && response.body().length() <= MAX_RESPONSE_BUFFER_SIZE;
    if (!shouldDisconnectResponseBody) {
      return response;
    }

    try {
      final byte[] bodyData = Util.toByteArray(response.body().asInputStream());
      return response.toBuilder().body(bodyData).build();
    } finally {
      ensureClosed(response.body());
    }
  }

  private Object decode(Response response, Type type) throws IOException {
    if (isVoidType(type)) {
      ensureClosed(response.body());
      return null;
    }

    try {
      final Object result = responseInterceptor.aroundDecode(
          new InvocationContext(decoder, type, response));
      if (closeAfterDecode) {
        ensureClosed(response.body());
      }
      return result;
    } catch (Exception e) {
      ensureClosed(response.body());
      throw e;
    }
  }

  private Exception decodeError(String methodKey, Response response) {
    try {
      return errorDecoder.decode(methodKey, response);
    } finally {
      ensureClosed(response.body());
    }
  }
}
