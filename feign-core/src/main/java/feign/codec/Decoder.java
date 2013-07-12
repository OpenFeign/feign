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

import static feign.Util.ensureClosed;

import feign.Response;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;

/**
 * Decodes an HTTP response into a given type. Invoked when {@link Response#status()} is in the 2xx
 * range. <br>
 * Ex. <br>
 *
 * <pre>
 * public class GsonDecoder extends Decoder {
 *   private final Gson gson;
 *
 *   public GsonDecoder(Gson gson) {
 *     this.gson = gson;
 *   }
 *
 *   &#064;Override
 *   public Object decode(String methodKey, Reader reader, Type type) {
 *     return gson.fromJson(reader, type);
 *   }
 * }
 * </pre>
 *
 * <br>
 * <br>
 * <br>
 * <b>Error handling</b><br>
 * <br>
 * Responses where {@link Response#status()} is not in the 2xx range are classified as errors,
 * addressed by the {@link ErrorDecoder}. That said, certain RPC apis return errors defined in the
 * {@link Response#body()} even on a 200 status. For example, in the DynECT api, a job still running
 * condition is returned with a 200 status, encoded in json. When scenarios like this occur, you
 * should raise an application-specific exception (which may be {@link feign.RetryableException
 * retryable}).
 */
public abstract class Decoder {

  /**
   * Override this method in order to consider the HTTP {@link Response} as opposed to just the
   * {@link feign.Response.Body} when decoding into a new instance of {@code type}.
   *
   * @param methodKey {@link feign.Feign#configKey} of the java method that invoked the request. ex.
   *     {@code IAM#getUser()}
   * @param response HTTP response.
   * @param type Target object type.
   * @return instance of {@code type}
   * @throws IOException if there was a network error reading the response.
   * @throws Exception if the decoder threw a checked exception.
   */
  public Object decode(String methodKey, Response response, Type type) throws Exception {
    Response.Body body = response.body();
    if (body == null) return null;
    Reader reader = body.asReader();
    try {
      return decode(methodKey, reader, type);
    } finally {
      ensureClosed(body);
    }
  }

  /**
   * Implement this to decode a {@code Reader} to an object of the specified type.
   *
   * @param methodKey {@link feign.Feign#configKey} of the java method that invoked the request. ex.
   *     {@code IAM#getUser()}
   * @param reader no need to close this, as {@link #decode(String, Response, Type)} manages
   *     resources.
   * @param type Target object type.
   * @return instance of {@code type}
   * @throws IOException will be propagated safely to the caller.
   * @throws Exception if the decoder threw a checked exception.
   */
  public abstract Object decode(String methodKey, Reader reader, Type type) throws Exception;
}
