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

import java.lang.reflect.Type;

import feign.RequestTemplate;
import feign.Util;

import static java.lang.String.format;

/**
 * Encodes an object into an HTTP request body. Like {@code javax.websocket.Encoder}. {@code
 * Encoder} is used when a method parameter has no {@code @Param} annotation. For example: <br>
 * <p/>
 * <pre>
 * &#064;POST
 * &#064;Path(&quot;/&quot;)
 * void create(User user);
 * </pre>
 * Example implementation: <br> <p/>
 * <pre>
 * public class GsonEncoder implements Encoder {
 *   private final Gson gson;
 *
 *   public GsonEncoder(Gson gson) {
 *     this.gson = gson;
 *   }
 *
 *   &#064;Override
 *   public void encode(Object object, Type bodyType, RequestTemplate template) {
 *     template.body(gson.toJson(object, bodyType));
 *   }
 * }
 * </pre>
 *
 * <p><h3>Form encoding</h3> <p>If any parameters are found in {@link
 * feign.MethodMetadata#formParams()}, they will be collected and passed to the Encoder as a map.
 *
 * <p>Ex. The following is a form. Notice the parameters aren't consumed in the request line. A map
 * including "username" and "password" keys will passed to the encoder, and the body type will be
 * {@link #MAP_STRING_WILDCARD}.
 * <pre>
 * &#064;RequestLine(&quot;POST /&quot;)
 * Session login(@Param(&quot;username&quot;) String username, @Param(&quot;password&quot;) String
 * password);
 * </pre>
 */
public interface Encoder {
  /** Type literal for {@code Map<String, ?>}, indicating the object to encode is a form. */
  Type MAP_STRING_WILDCARD = Util.MAP_STRING_WILDCARD;

  /**
   * Converts objects to an appropriate representation in the template.
   *
   * @param object   what to encode as the request body.
   * @param bodyType the type the object should be encoded as. {@link #MAP_STRING_WILDCARD}
   *                 indicates form encoding.
   * @param template the request template to populate.
   * @throws EncodeException when encoding failed due to a checked exception.
   */
  void encode(Object object, Type bodyType, RequestTemplate template) throws EncodeException;

  /**
   * Default implementation of {@code Encoder}.
   */
  class Default implements Encoder {

    @Override
    public void encode(Object object, Type bodyType, RequestTemplate template) {
      if (bodyType == String.class) {
        template.body(object.toString());
      } else if (bodyType == byte[].class) {
        template.body((byte[]) object, null);
      } else if (object != null) {
        throw new EncodeException(
            format("%s is not a type supported by this encoder.", object.getClass()));
      }
    }
  }
}
