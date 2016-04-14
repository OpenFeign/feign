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

import java.io.IOException;
import java.lang.reflect.Type;

import feign.Feign;
import feign.FeignException;
import feign.Response;
import feign.Util;

/**
 * Decodes an HTTP response into a single object of the given {@code type}. Invoked when {@link
 * Response#status()} is in the 2xx range and the return type is neither {@code void} nor {@code
 * Response}. <p/> <p/> Example Implementation:<br> <p/>
 * <pre>
 * public class GsonDecoder implements Decoder {
 *   private final Gson gson = new Gson();
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
 * <br/> <h3>Implementation Note</h3> The {@code type} parameter will correspond to the {@link
 * java.lang.reflect.Method#getGenericReturnType() generic return type} of an {@link
 * feign.Target#type() interface} processed by {@link feign.Feign#newInstance(feign.Target)}.  When
 * writing your implementation of Decoder, ensure you also test parameterized types such as {@code
 * List<Foo>}.
 * <br/> <h3>Note on exception propagation</h3> Exceptions thrown by {@link Decoder}s get wrapped in
 * a {@link DecodeException} unless they are a subclass of {@link FeignException} already, and unless
 * the client was configured with {@link Feign.Builder#decode404()}.
 */
public interface Decoder {

  /**
   * Decodes an http response into an object corresponding to its {@link
   * java.lang.reflect.Method#getGenericReturnType() generic return type}. If you need to wrap
   * exceptions, please do so via {@link DecodeException}.
   *
   * @param response the response to decode
   * @param type     {@link java.lang.reflect.Method#getGenericReturnType() generic return type} of
   *                 the method corresponding to this {@code response}.
   * @return instance of {@code type}
   * @throws IOException     will be propagated safely to the caller.
   * @throws DecodeException when decoding failed due to a checked exception besides IOException.
   * @throws FeignException  when decoding succeeds, but conveys the operation failed.
   */
  Object decode(Response response, Type type) throws IOException, DecodeException, FeignException;

  /** Default implementation of {@code Decoder}. */
  public class Default extends StringDecoder {

    @Override
    public Object decode(Response response, Type type) throws IOException {
      if (response.status() == 404) return Util.emptyValueOf(type);
      if (response.body() == null) return null;
      if (byte[].class.equals(type)) {
        return Util.toByteArray(response.body().asInputStream());
      }
      return super.decode(response, type);
    }
  }
}
