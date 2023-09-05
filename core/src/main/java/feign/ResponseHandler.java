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

import static feign.FeignException.errorReading;
import static feign.Util.ensureClosed;
import feign.Logger.Level;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.lang.reflect.Type;

/**
 * The response handler that is used to provide synchronous support on top of standard response
 * handling
 */
public class ResponseHandler {

  private final Level logLevel;
  private final Logger logger;

  private final Decoder decoder;
  private final ErrorDecoder errorDecoder;
  private final boolean dismiss404;
  private final boolean closeAfterDecode;

  private final boolean decodeVoid;

  private final ResponseInterceptor.Chain executionChain;

  public ResponseHandler(Level logLevel, Logger logger, Decoder decoder, ErrorDecoder errorDecoder,
      boolean dismiss404, boolean closeAfterDecode, boolean decodeVoid,
      ResponseInterceptor.Chain executionChain) {
    super();
    this.logLevel = logLevel;
    this.logger = logger;
    this.decoder = decoder;
    this.errorDecoder = errorDecoder;
    this.dismiss404 = dismiss404;
    this.closeAfterDecode = closeAfterDecode;
    this.decodeVoid = decodeVoid;
    this.executionChain = executionChain;
  }

  public Object handleResponse(String configKey,
                               Response response,
                               Type returnType,
                               long elapsedTime)
      throws Exception {
    try {
      response = logAndRebufferResponseIfNeeded(configKey, response, elapsedTime);
      return executionChain.next(
          new InvocationContext(configKey, decoder, errorDecoder, dismiss404, closeAfterDecode,
              decodeVoid, response, returnType));
    } catch (final IOException e) {
      if (logLevel != Level.NONE) {
        logger.logIOException(configKey, logLevel, e, elapsedTime);
      }
      throw errorReading(response.request(), response, e);
    } catch (Exception e) {
      ensureClosed(response.body());
      throw e;
    }
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
}
