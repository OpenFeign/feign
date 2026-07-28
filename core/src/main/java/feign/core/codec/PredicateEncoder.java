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
package feign.core.codec;

import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.codec.EncoderPredicate;
import java.lang.reflect.Type;

/**
 * An encoder that wraps another (delegate) encoder but blocks encoding if a predicate isn't met.
 * For example, this is useful for restricting the usage of a given Encoder to a given content type header. 
 */
public class PredicateEncoder implements Encoder {
	
  /**
   * The delegate that the encoding request will be passed to if the predicate is met.
   */
  private final Encoder delegate;
  private final EncoderPredicate predicate;

  public PredicateEncoder(Encoder delegate, EncoderPredicate predicate) {
    this.delegate = delegate;
    this.predicate = predicate;
  }

  @Override
  public boolean encode(Object object, Type bodyType, RequestTemplate template)
      throws EncodeException {
    if (predicate.test(object, bodyType, template))
      return delegate.encode(object, bodyType, template);

    return false;
  }
}
