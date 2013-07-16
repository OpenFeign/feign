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
import feign.IncrementalCallback;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;

/**
 * Decodes an HTTP response incrementally into an {@link IncrementalCallback}
 * via a series of {@link IncrementalCallback#onNext(Object) onNext} calls.
 * <p/>
 * Invoked when {@link feign.Response#status()} is in the 2xx range.
 *
 * @param <I> input that can be derived from {@link feign.Response.Body}.
 * @param <T> widest type an instance of this can decode.
 */
public interface IncrementalDecoder<I, T> {
  /**
   * Implement this to decode a resource to an object into a single object.
   * If you need to wrap exceptions, please do so via {@link feign.codec.DecodeException}.
   * <br>
   * Do not call {@link feign.IncrementalCallback#onSuccess() onSuccess} or
   * {@link feign.IncrementalCallback#onFailure onFailure}.
   *
   * @param input               if {@code Closeable}, no need to close this, as the caller
   *                            manages resources.
   * @param type                type parameter of {@link feign.IncrementalCallback#onNext}.
   * @param incrementalCallback call {@link feign.IncrementalCallback#onNext onNext}
   *                            each time an object of {@code type} is decoded
   *                            from the response.
   * @throws java.io.IOException         will be propagated safely to the caller.
   * @throws feign.codec.DecodeException when decoding failed due to a checked exception
   *                                     besides IOException.
   * @throws feign.FeignException        when decoding succeeds, but conveys the operation
   *                                     failed.
   */
  void decode(I input, Type type, IncrementalCallback<? super T> incrementalCallback)
      throws IOException, DecodeException, FeignException;

  /**
   * Used for text-based apis, follows
   * {@link feign.codec.IncrementalDecoder#decode(Object, java.lang.reflect.Type, IncrementalCallback)}
   * semantics, applied to inputs of type {@link java.io.Reader}. <br>
   * Ex. <br>
   * <p/>
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
   * <pre>
   * public class GsonIncrementalDecoder implements IncrementalDecoder<Object> {
   *   private final Gson gson;
   *
   *   public GsonIncrementalDecoder(Gson gson) {
   *     this.gson = gson;
   *   }
   *
   *   &#064;Override public void decode(Reader reader, Type type, IncrementalCallback<? super Object> incrementalCallback) throws Exception {
   *     JsonReader jsonReader = new JsonReader(reader);
   *     jsonReader.beginArray();
   *     while (jsonReader.hasNext()) {
   *       try {
   *          incrementalCallback.onNext(gson.fromJson(jsonReader, type));
   *       } catch (JsonIOException e) {
   *         if (e.getCause() != null &amp;&amp;
   *             e.getCause() instanceof IOException) {
   *           throw IOException.class.cast(e.getCause());
   *         }
   *         throw e;
   *       }
   *     }
   *     jsonReader.endArray();
   *   }
   * }
   * </pre>
   */
  public interface TextStream<T> extends IncrementalDecoder<Reader, T> {
  }
}
