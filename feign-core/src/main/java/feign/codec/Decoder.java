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

import com.google.common.io.Closer;
import com.google.common.reflect.TypeToken;
import feign.Response;
import java.io.IOException;
import java.io.Reader;

/**
 * Decodes an HTTP response into a given type. Invoked when {@link Response#status()} is in the 2xx
 * range.
 *
 * <p>Ex.
 *
 * <p>
 *
 * <pre>
 * public class GsonDecoder extends Decoder {
 *     private final Gson gson;
 *
 *     public GsonDecoder(Gson gson) {
 *    this.gson = gson;
 *     }
 *
 *     &#064;Override
 *     public Object decode(String methodKey, Reader reader, TypeToken&lt;?&gt; type) {
 *    return gson.fromJson(reader, type.getType());
 *     }
 * }
 * </pre>
 *
 * <p>
 *
 * <h4>Error handling</h4>
 *
 * <p>Responses where {@link Response#status()} is not in the 2xx range are classified as errors,
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
   */
  public Object decode(String methodKey, Response response, TypeToken<?> type) throws IOException {
    Response.Body body = response.body().orNull();
    if (body == null) return null;
    Closer closer = Closer.create();
    try {
      Reader reader = closer.register(body.asReader());
      return decode(methodKey, reader, type);
    } catch (IOException e) {
      throw closer.rethrow(e, IOException.class);
    } catch (Throwable e) {
      throw closer.rethrow(e);
    } finally {
      closer.close();
    }
  }

  /**
   * Implement this to decode a {@code Reader} to an object of the specified type.
   *
   * @param methodKey {@link feign.Feign#configKey} of the java method that invoked the request. ex.
   *     {@code IAM#getUser()}
   * @param reader no need to close this, as {@link #decode(String, Response, TypeToken)} manages
   *     resources.
   * @param type Target object type.
   * @return instance of {@code type}
   * @throws Throwable will be propagated safely to the caller.
   */
  public abstract Object decode(String methodKey, Reader reader, TypeToken<?> type)
      throws Throwable;
}
