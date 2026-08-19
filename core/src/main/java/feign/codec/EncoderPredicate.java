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

import feign.Experimental;
import feign.RequestTemplate;
import java.lang.reflect.Type;

/**
 * A predicate that decides whether a given request can be handled by an {@link Encoder}.
 *
 * <p>Predicates receive the same three arguments as {@link Encoder#encode(Object, Type,
 * RequestTemplate)}, so they can discriminate on the body, on its declared type, or on anything
 * already present in the template such as the {@code Content-Type} header.
 *
 * @see PredicatedEncoder
 * @see MultiEncoder
 */
@FunctionalInterface
@Experimental
public interface EncoderPredicate {

  /**
   * Tests whether the given request can be encoded.
   *
   * @param object what would be encoded as the request body
   * @param bodyType the type the object would be encoded as. {@link Encoder#MAP_STRING_WILDCARD}
   *     indicates form encoding.
   * @param template the request template that would be populated
   * @return {@code true} if the request can be encoded, {@code false} otherwise
   */
  boolean test(Object object, Type bodyType, RequestTemplate template);
}
