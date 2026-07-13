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
import java.util.List;

/**
 * An encoder that encodes objects to XML.
 *
 * @since 14
 */
@FunctionalInterface
public interface XmlEncoder extends Encoder {
  /**
   * Checks if the given object can be encoded to XML based on the {@code Content-Type} header in
   * the {@link RequestTemplate}.
   *
   * @param object {@inheritDoc}
   * @param bodyType {@inheritDoc}
   * @param template {@inheritDoc}
   * @return {@code true} if the given {@code Content-Type} header is compatible with XML, {@code
   *     false} otherwise
   */
  @Override
  default boolean canEncode(Object object, Type bodyType, RequestTemplate template) {
    return template.headers().getOrDefault("Content-Type", List.of()).stream()
        .anyMatch(
            contentType ->
                contentType != null
                    && contentType.trim().matches("(?i)\\w+/(?:[\\w._-]+\\+)?xml.*"));
  }
}
