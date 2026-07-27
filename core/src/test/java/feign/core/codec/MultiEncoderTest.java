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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feign.RequestTemplate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DelegatingEncoderTest {
  @Mock private Encoder delegate;
  private Encoder encoder;

  @BeforeEach
  void setUp() {
    encoder = new DelegatingEncoder(List.of(delegate));
  }

  @Test
  void shouldEncode() {
    var object = "Hello, World!";
    var bodyType = String.class;
    var requestTemplate = mock(RequestTemplate.class);

    when(delegate.encode(object, bodyType, requestTemplate)).thenReturn(true);

    encoder.encode(object, bodyType, requestTemplate);

    verify(delegate).encode(object, bodyType, requestTemplate);
  }

  @Test
  void shouldThrowEncodeExceptionWhenNoSuitableEncoderFound() {
    var object = "Hello, World!";
    var bodyType = String.class;
    var requestTemplate = mock(RequestTemplate.class);

    assertThrows(EncodeException.class, () -> encoder.encode(object, bodyType, requestTemplate));
  }
}
