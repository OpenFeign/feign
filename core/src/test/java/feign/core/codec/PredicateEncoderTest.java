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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import feign.RequestTemplate;
import feign.codec.Encoder;
import feign.codec.EncoderPredicate;

@ExtendWith(MockitoExtension.class)
class PredicateEncoderTest {

  @Mock private Encoder delegate;
  @Mock private EncoderPredicate encoderPredicate;
  @InjectMocks private PredicateEncoder predicateEncoder;

  @Test
  void shouldEncode() {

	String object = "Hello, World!";
	Class<String> bodyType = String.class;
	RequestTemplate requestTemplate = mock(RequestTemplate.class);
	  
	when(encoderPredicate.test(object, bodyType, requestTemplate)).thenReturn(true);
    when(delegate.encode(object, bodyType, requestTemplate)).thenReturn(true);

    assertThat(predicateEncoder.encode(object, bodyType, requestTemplate)).isTrue();

    verify(delegate).encode(object, bodyType, requestTemplate);
  }

  @Test
  void shouldNotEncode() {
    var object = "Hello, World!";
    var bodyType = String.class;
    var requestTemplate = mock(RequestTemplate.class);
	  
    when(encoderPredicate.test(object, bodyType, requestTemplate)).thenReturn(false);

    assertThat(predicateEncoder.encode(object, bodyType, requestTemplate)).isFalse();

    verifyNoInteractions(delegate);
  }
}
