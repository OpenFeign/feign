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
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;

/**
 * Decodes an HTTP response into a given type. Invoked when {@link Response#status()} is in the 2xx
 * range. Like {@code javax.websocket.Decoder}, except that the decode method is passed the generic
 * type of the target. <br>
 *
 * @param <I> input that can be derived from {@link feign.Response.Body}.
 * @param <T> widest type an instance of this can decode.
 */
public interface Decoder<I, T> {
  /**
   * Implement this to decode a resource to an object into a single object. If you need to wrap
   * exceptions, please do so via {@link DecodeException}.
   *
   * @param input if {@code Closeable}, no need to close this, as the caller manages resources.
   * @param type Target object type.
   * @return instance of {@code type}
   * @throws IOException will be propagated safely to the caller.
   * @throws DecodeException when decoding failed due to a checked exception besides IOException.
   * @throws FeignException when decoding succeeds, but conveys the operation failed.
   */
  T decode(I input, Type type) throws IOException, DecodeException, FeignException;

  /**
   * Used for text-based apis, follows {@link Decoder#decode(Object, java.lang.reflect.Type)}
   * semantics, applied to inputs of type {@link java.io.Reader}. <br>
   * Ex. <br>
   *
   * <p>
   *
   * <pre>
   * public class GsonDecoder implements Decoder.TextStream&lt;Object&gt; {
   *   private final Gson gson;
   *
   *   public GsonDecoder(Gson gson) {
   *     this.gson = gson;
   *   }
   *
   *   &#064;Override
   *   public Object decode(Reader reader, Type type) throws IOException {
   *     try {
   *       return gson.fromJson(reader, type);
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
  public interface TextStream<T> extends Decoder<Reader, T> {}
}
