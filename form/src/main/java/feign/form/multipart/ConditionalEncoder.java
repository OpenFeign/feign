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
package feign.form.multipart;

import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import java.lang.reflect.Type;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * An encoder that delegates to another encoder if the given object is supported by the provided
 * predicate. Otherwise, an {@link EncodeException} is thrown.
 */
@RequiredArgsConstructor
public class ConditionalEncoder implements Encoder {
  @NonNull private final Encoder delegate;
  @NonNull private final EncoderPredicate encoderPredicate;

  /**
   * Encodes the given object as the request body if it is supported by the provided predicate.
   *
   * @param object {@inheritDoc}
   * @param bodyType {@inheritDoc}
   * @param template {@inheritDoc}
   * @throws EncodeException {@inheritDoc}
   */
  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template)
      throws EncodeException {
    if (!encoderPredicate.test(object, bodyType, template)) {
      throw new EncodeException(
          String.format(
              "Unsupported object received for encoding: %s, type: %s", object, bodyType));
    }

    delegate.encode(object, bodyType, template);
  }
}
