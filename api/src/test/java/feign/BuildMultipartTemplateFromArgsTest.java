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
package feign;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import feign.codec.EncodeException;
import feign.codec.Encoder;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BuildMultipartTemplateFromArgsTest {
  private static final String PAYLOAD = "Hello, World!";
  private static final String PARAM_VALUE = "paramValue";
  private static final Map<String, Object> VARIABLES = Map.of("paramName", PARAM_VALUE);

  @Mock private Encoder encoder;
  private RequestTemplate template;
  private MultipartFormData formData;
  private RequestTemplate.Factory factory;

  @BeforeEach
  void setUp() {
    var partIndex = 0;
    var varIndex = 1;
    var paramName = "paramName";
    var type = String.class;
    var unwrap = true;

    var headers =
        Map.<String, Collection<String>>of(
            "Content-Disposition", List.of("form-data; name=\"data\""),
            "Content-Type", List.of("text/plain"));

    template = spy(new RequestTemplate());
    formData =
        new MultipartFormData(List.of(new PartData(type, PAYLOAD, headers, unwrap)), VARIABLES);

    var methodMetadata = new MethodMetadata();

    methodMetadata
        .partMetadata()
        .put(partIndex, new PartMetadata(partIndex, type, headers, unwrap));
    methodMetadata.indexToName().put(varIndex, List.of(paramName));

    factory = new RequestTemplateFactoryResolver(encoder, mock()).resolve(mock(), methodMetadata);
  }

  @Test
  void shouldResolveMultipartTemplate() {
    try (var requestTemplate = mockStatic(RequestTemplate.class)) {
      requestTemplate.when(() -> RequestTemplate.from(any())).thenReturn(template);

      factory.create(new Object[] {PAYLOAD, PARAM_VALUE});
    }

    verify(encoder).encode(eq(formData), eq(MultipartFormData.class), notNull());
    verify(template).resolve(VARIABLES);
  }

  @Test
  void shouldRethrowEncodeException() {
    var expected = mock(EncodeException.class);

    doThrow(expected).when(encoder).encode(eq(formData), eq(MultipartFormData.class), notNull());

    try (var requestTemplate = mockStatic(RequestTemplate.class)) {
      requestTemplate.when(() -> RequestTemplate.from(any())).thenReturn(template);

      assertThatThrownBy(() -> factory.create(new Object[] {PAYLOAD, PARAM_VALUE}))
          .isEqualTo(expected);
    }

    verify(encoder).encode(eq(formData), eq(MultipartFormData.class), notNull());
  }

  @Test
  void shouldWrapRuntimeExceptionIntoEncodeException() {
    var expected = new RuntimeException("Test error");

    doThrow(expected).when(encoder).encode(eq(formData), eq(MultipartFormData.class), notNull());

    try (var requestTemplate = mockStatic(RequestTemplate.class)) {
      requestTemplate.when(() -> RequestTemplate.from(any())).thenReturn(template);

      assertThatThrownBy(() -> factory.create(new Object[] {PAYLOAD, PARAM_VALUE}))
          .isInstanceOf(EncodeException.class)
          .hasCause(expected);
    }

    verify(encoder).encode(eq(formData), eq(MultipartFormData.class), notNull());
  }
}
