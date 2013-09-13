/*
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.codec;

import feign.FeignException;
import feign.Response;
import feign.Util;

import java.io.IOException;
import java.lang.reflect.Type;

import static java.lang.String.format;

/**
 * Decodes an HTTP response into a single object of the given {@code Type}. Invoked when
 * {@link Response#status()} is in the 2xx range. Like
 * {@code javax.websocket.Decoder}, except that the decode method is passed the
 * generic type of the target.
 *
 * <p>
 * Example Implementation:<br>
 * <p/>
 * <pre>
 * public class GsonDecoder implements Decoder {
 *   private final Gson gson;
 *
 *   public GsonDecoder(Gson gson) {
 *     this.gson = gson;
 *   }
 *
 *   &#064;Override
 *   public Object decode(Response response, Type type) throws IOException {
 *     try {
 *       return gson.fromJson(response.body().asReader(), type);
 *     } catch (JsonIOException e) {
 *       if (e.getCause() != null &amp;&amp;
 *           e.getCause() instanceof IOException) {
 *         throw IOException.class.cast(e.getCause());
 *       }
 *       throw e;
 *     }
 *   }
 * }
 * </pre>
 */
public interface Decoder {
  /**
   * Decodes a response into a single object.
   * If you need to wrap exceptions, please do so via {@link DecodeException}.
   *
   * @param response the response to decode
   * @param type  Target object type.
   * @return instance of {@code type}
   * @throws IOException     will be propagated safely to the caller.
   * @throws DecodeException when decoding failed due to a checked exception besides IOException.
   * @throws FeignException  when decoding succeeds, but conveys the operation failed.
   */
  Object decode(Response response, Type type) throws IOException, DecodeException, FeignException;

  /**
   * Default implementation of {@code Decoder} that supports {@code void}, {@code Response}, and {@code String}
   * signatures.
   */
  public class Default implements Decoder {
    @Override
    public Object decode(Response response, Type type) throws IOException {
      if (Response.class.equals(type)) {
        String bodyString = null;
        if (response.body() != null) {
          bodyString = Util.toString(response.body().asReader());
        }
        return Response.create(response.status(), response.reason(), response.headers(), bodyString);
      } else if (void.class.equals(type) || response.body() == null) {
        return null;
      } else if (String.class.equals(type)) {
        return Util.toString(response.body().asReader());
      }
      throw new DecodeException(format("%s is not a type supported by this decoder.", type));
    }
  }
}
