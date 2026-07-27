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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feign.RequestTemplate;
import feign.codec.Encoder;
import feign.codec.EncoderPredicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PredicateEncoderTest {

  String object = "Hello, World!";
  Class<String> bodyType = String.class;
  RequestTemplate requestTemplate = mock(RequestTemplate.class);

  @Mock private Encoder delegate;
  private final EncoderPredicate truePredicate = (o, t, tt) -> true;
  private final EncoderPredicate falsePredicate = (o, t, tt) -> false;

  @Test
  void shouldEncode() {

    when(delegate.encode(object, bodyType, requestTemplate)).thenReturn(true);

    PredicateEncoder predicateEncoder = new PredicateEncoder(delegate, truePredicate);

    assertThat(predicateEncoder.encode(object, bodyType, requestTemplate)).isTrue();

    verify(delegate).encode(object, bodyType, requestTemplate);
  }

  @Test
  void shouldNotEncode() {
    PredicateEncoder predicateEncoder = new PredicateEncoder(delegate, falsePredicate);

    assertThat(predicateEncoder.encode(object, bodyType, requestTemplate)).isFalse();

    verify(delegate, never()).encode(object, bodyType, requestTemplate);
  }
}
