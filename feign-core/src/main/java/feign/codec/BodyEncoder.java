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

import feign.RequestTemplate;

public interface BodyEncoder {
  /**
   * Converts objects to an appropriate representation. Can affect any part of {@link
   * RequestTemplate}. <br>
   * Ex. <br>
   *
   * <pre>
   * public class GsonEncoder implements BodyEncoder {
   *   private final Gson gson;
   *
   *   public GsonEncoder(Gson gson) {
   *     this.gson = gson;
   *   }
   *
   *   &#064;Override
   *   public void encodeBody(Object bodyParam, RequestTemplate base) {
   *     base.body(gson.toJson(bodyParam));
   *   }
   * }
   * </pre>
   *
   * <br>
   * If a parameter has no {@code *Param} annotation, it is passed to this method. <br>
   *
   * <pre>
   * &#064;POST
   * &#064;Path(&quot;/&quot;)
   * void create(User user);
   * </pre>
   *
   * @param bodyParam a body parameter
   * @param base template to encode the {@code object} into.
   */
  void encodeBody(Object bodyParam, RequestTemplate base);
}
