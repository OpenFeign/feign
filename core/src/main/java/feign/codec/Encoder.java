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

/**
 * Encodes an object into an HTTP request body. Like
 * {@code javax.websocket.Encoder}. <br>
 * {@code Encoder} is used when a method parameter has no {@code *Param}
 * annotation. For example: <br>
 * <p/>
 * <pre>
 * &#064;POST
 * &#064;Path(&quot;/&quot;)
 * void create(User user);
 * </pre>
 * <p/>
 * <h3>Form encoding</h3>
 * <br>
 * If any parameters are found in {@link feign.MethodMetadata#formParams()}, they will be
 * collected and passed to {@code Encoder.Text<Map<String, ?>>}.
 * <br>
 * <pre>
 * &#064;POST
 * &#064;Path(&quot;/&quot;)
 * Session login(@Named(&quot;username&quot;) String username, @Named(&quot;password&quot;) String password);
 * </pre>
 *
 * @param <T> widest type an instance of this can encode.
 */
public interface Encoder<T> {

  /**
   * Converts objects to an appropriate text representation. <br>
   * Ex. <br>
   * <p/>
   * <pre>
   * public class GsonEncoder implements Encoder.Text&lt;Object&gt; {
   *     private final Gson gson;
   *
   *     public GsonEncoder(Gson gson) {
   *         this.gson = gson;
   *     }
   *
   *     &#064;Override
   *     public String encode(Object object) {
   *         return gson.toJson(object);
   *     }
   * }
   * </pre>
   */
  interface Text<T> extends Encoder<T> {
    /**
     * Implement this to encode an object as a String.. If you need to wrap
     * exceptions, please do so via {@link EncodeException}
     *
     * @param object what to encode as the request body.
     * @return the encoded object as a string. * @throws EncodeException
     *         when encoding failed due to a checked exception.
     */
    String encode(T object) throws EncodeException;
  }
}
