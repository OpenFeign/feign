/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
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
import java.lang.reflect.Type;

/** A predicate that determines whether a given object can be encoded by an encoder. */
@FunctionalInterface
public interface EncoderPredicate {

  /**
   * Tests whether the given object can be encoded by an encoder.
   *
   * @param object the object to be encoded
   * @param bodyType the type of the object to be encoded
   * @param template the request template that will be used to encode the object
   * @return {@code true} if the object can be encoded, {@code false} otherwise
   */
  boolean test(Object object, Type bodyType, RequestTemplate template);
}
