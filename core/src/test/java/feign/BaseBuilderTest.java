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

import feign.Logger.NoOpLogger;
import feign.codec.Decoder;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BaseBuilderTest {

  @Test
  void checkEnrichTouchesAllAsyncBuilderFields()
      throws IllegalArgumentException, IllegalAccessException {
    test(
        AsyncFeign.builder().requestInterceptor(_ -> {}).responseInterceptor((ic, c) -> c.next(ic)),
        12);
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
        Feign.builder().requestInterceptor(_ -> {}).responseInterceptor((ic, c) -> c.next(ic)), 10);
  }

  @Test
  void checkEnrichSkipsFinalAndStaticFields() {
    Capability mockingCapability = Mockito.mock(Capability.class, RETURNS_MOCKS);

    BuilderWithFinalAndStaticFields builder = new BuilderWithFinalAndStaticFields();
    BuilderWithFinalAndStaticFields enriched =
        (BuilderWithFinalAndStaticFields) builder.addCapability(mockingCapability).enrich();

    assertThat(Mockito.mockingDetails(enriched.decoder).isMock())
        .as("Mutable fields must still be enriched")
        .isTrue();
    assertThat(Mockito.mockingDetails(enriched.finalLogger).isMock())
        .as("Final fields must not be enriched")
        .isFalse();
    assertThat(enriched.finalLogger).isSameAs(builder.finalLogger);
    assertThat(Mockito.mockingDetails(BuilderWithFinalAndStaticFields.STATIC_LOGGER).isMock())
        .as("Static fields must not be enriched")
        .isFalse();
  }

  @Test
  void clonedBuilderFluentMethodsReturnClone() throws CloneNotSupportedException {
    CloneableFeignBuilder original = new CloneableFeignBuilder();
    Feign.Builder clone = original.copy();

    assertThat(clone.requestInterceptor(_ -> {})).isSameAs(clone);
    assertThat(clone.decoder(Mockito.mock(Decoder.class))).isSameAs(clone);
  }

  private static final class CloneableFeignBuilder extends Feign.Builder {

    Feign.Builder copy() throws CloneNotSupportedException {
      return (Feign.Builder) super.clone();
    }
  }

  /**
   * A builder subclass holding final and static fields, mirroring the {@code private static final
   * Logger} declaration from issue #1835.
   */
  private static class BuilderWithFinalAndStaticFields extends Feign.Builder {

    private static final Logger STATIC_LOGGER = new NoOpLogger();

    private final Logger finalLogger = new NoOpLogger();
  }

  @Test
  void capabilityCanProvideResponseInterceptorWhenNoneConfigured() {
    AtomicInteger enrichCalls = new AtomicInteger();
    ResponseInterceptor capabilityInterceptor = (context, chain) -> chain.next(context);

    Feign.Builder enrichedBuilder =
        Feign.builder()
            .addCapability(
                new Capability() {
                  @Override
                  public ResponseInterceptor enrich(ResponseInterceptor responseInterceptor) {
                    enrichCalls.incrementAndGet();
                    return capabilityInterceptor;
                  }
                })
            .enrich();

    assertThat(enrichCalls).hasValue(1);
    assertThat(enrichedBuilder.responseInterceptors).containsExactly(capabilityInterceptor);
  }
}
