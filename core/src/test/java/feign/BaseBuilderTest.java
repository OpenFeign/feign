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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BaseBuilderTest {

  @Test
  void checkEnrichTouchesAllAsyncBuilderFields()
      throws IllegalArgumentException, IllegalAccessException {
    Capability mockingCapability =
        test(
            AsyncFeign.builder()
                .requestInterceptor(template -> {})
                .responseInterceptor((ic, c) -> c.next(ic)),
            14);

    // make sure capability was invoked only once
    verify(mockingCapability).enrich(any(AsyncClient.class));
  }

  @Test
  void checkEnrichTouchesAllBuilderFields()
      throws IllegalArgumentException, IllegalAccessException {
    Capability mockingCapability =
        test(
            Feign.builder()
                .requestInterceptor(template -> {})
                .responseInterceptor((ic, c) -> c.next(ic)),
            12);

    // make sure capability was invoked only once
    verify(mockingCapability).enrich(any(Client.class));
  }

  private Capability test(BaseBuilder<?, ?> builder, int expectedFieldsCount)
      throws IllegalArgumentException, IllegalAccessException {
    Capability mockingCapability = mock(Capability.class, RETURNS_MOCKS);
    doAnswer(inv -> inv.getArgument(0, BaseBuilder.class))
        .when(mockingCapability)
        .beforeBuild(any(BaseBuilder.class));

    BaseBuilder<?, ?> enriched = builder.addCapability(mockingCapability).enrich();

    List<Field> fields = enriched.getFieldsToEnrich();
    assertThat(fields).hasSize(expectedFieldsCount);

    for (Field field : fields) {
      field.setAccessible(true);
      Object mockedValue = field.get(enriched);
      if (mockedValue instanceof List<?> list) {
        assertThat(list).withFailMessage("Enriched list missing contents %s", field).isNotEmpty();
        mockedValue = list.getFirst();
      }
      assertThat(Mockito.mockingDetails(mockedValue).isMock())
          .as("Field was not enriched " + field)
          .isTrue();
      assertNotSame(builder, enriched);
    }

    // make sure capability was invoked only once
    verify(mockingCapability).beforeBuild(any(BaseBuilder.class));

    return mockingCapability;
  }
}
