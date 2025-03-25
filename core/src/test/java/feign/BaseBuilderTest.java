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
import static org.mockito.Mockito.RETURNS_MOCKS;

import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BaseBuilderTest {

  @Test
  void checkEnrichTouchesAllAsyncBuilderFields()
      throws IllegalArgumentException, IllegalAccessException {
    test(
        AsyncFeign.builder()
            .requestInterceptor(template -> {})
            .responseInterceptor((ic, c) -> c.next(ic)),
        14);
  }

  private void test(BaseBuilder<?, ?> builder, int expectedFieldsCount)
      throws IllegalArgumentException, IllegalAccessException {
    Capability mockingCapability = Mockito.mock(Capability.class, RETURNS_MOCKS);
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
  }

  @Test
  void checkEnrichTouchesAllBuilderFields()
      throws IllegalArgumentException, IllegalAccessException {
    test(
        Feign.builder()
            .requestInterceptor(template -> {})
            .responseInterceptor((ic, c) -> c.next(ic)),
        12);
  }

  @Test
  void checkCloneDontLooseInterceptors() {
    Feign.Builder originalBuilder =
        Feign.builder()
            .requestInterceptor(new FirstRequestInterceptor())
            .addCapability(
                new Capability() {
                  @Override
                  public RequestInterceptor enrich(RequestInterceptor requestInterceptor) {
                    return new DecoratingRequestInterceptor(requestInterceptor);
                  }
                });

    // There is one interceptor FirstRequestInterceptor
    assertThat(originalBuilder.requestInterceptors)
        .isNotNull()
        .isNotEmpty()
        .hasSize(1)
        .first()
        .isInstanceOf(FirstRequestInterceptor.class);

    Feign.Builder enrichedBuilder = originalBuilder.enrich();

    // Original builder should have one interceptor FirstRequestInterceptor
    assertThat(originalBuilder.requestInterceptors)
        .isNotNull()
        .isNotEmpty()
        .hasSize(1)
        .first()
        .isInstanceOf(FirstRequestInterceptor.class);

    // enrichedBuilder should have one interceptor DecoratingRequestInterceptor
    assertThat(enrichedBuilder.requestInterceptors)
        .isNotNull()
        .isNotEmpty()
        .hasSize(1)
        .first()
        .isInstanceOf(DecoratingRequestInterceptor.class);

    Feign.Builder enrichedBuilderWithInterceptor =
        enrichedBuilder.requestInterceptor(new SecondRequestInterceptor());

    // Original builder should have one interceptor FirstRequestInterceptor
    assertThat(originalBuilder.requestInterceptors)
        .isNotNull()
        .isNotEmpty()
        .hasSize(1)
        .first()
        .isInstanceOf(FirstRequestInterceptor.class);

    // enrichedBuilder should have two interceptors
    assertThat(enrichedBuilder.requestInterceptors).isNotNull().isNotEmpty().hasSize(2);
    assertThat(enrichedBuilder.requestInterceptors.get(0))
        .isInstanceOf(DecoratingRequestInterceptor.class);
    assertThat(enrichedBuilder.requestInterceptors.get(1))
        .isInstanceOf(SecondRequestInterceptor.class);

    // enrichedBuilderWithInterceptor should have two interceptors
    assertThat(enrichedBuilderWithInterceptor.requestInterceptors)
        .isNotNull()
        .isNotEmpty()
        .hasSize(2);
    assertThat(enrichedBuilderWithInterceptor.requestInterceptors.get(0))
        .isInstanceOf(DecoratingRequestInterceptor.class);
    assertThat(enrichedBuilderWithInterceptor.requestInterceptors.get(1))
        .isInstanceOf(SecondRequestInterceptor.class);
  }

  static final class FirstRequestInterceptor implements RequestInterceptor {
    @Override
    public void apply(final RequestTemplate template) {}
  }

  static final class SecondRequestInterceptor implements RequestInterceptor {
    @Override
    public void apply(final RequestTemplate template) {}
  }

  static final class DecoratingRequestInterceptor implements RequestInterceptor {
    RequestInterceptor delegate;

    DecoratingRequestInterceptor(RequestInterceptor delegate) {
      this.delegate = delegate;
    }

    @Override
    public void apply(final RequestTemplate template) {}
  }
}
