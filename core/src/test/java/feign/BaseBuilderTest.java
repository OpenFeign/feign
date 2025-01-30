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

import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
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

  @Test
  void checkEnrichTouchesAllBuilderFields()
      throws IllegalArgumentException, IllegalAccessException {
    test(
        Feign.builder()
            .requestInterceptor(template -> {})
            .responseInterceptor((ic, c) -> c.next(ic)),
        12);
  }

  private void test(BaseBuilder<?, ?> builder, int expectedFieldsCount)
      throws IllegalArgumentException, IllegalAccessException {
    Capability mockingCapability = new MockingCapability();

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

  private final class MockingCapability implements Capability {

    @Override
    @SuppressWarnings("unchecked")
    public <C> AsyncContextSupplier<C> enrich(AsyncContextSupplier<C> asyncContextSupplier) {
      return (AsyncContextSupplier<C>) Mockito.mock(AsyncContextSupplier.class);
    }

    @Override
    public AsyncResponseHandler enrich(AsyncResponseHandler asyncResponseHandler) {
      return Mockito.mock(AsyncResponseHandler.class);
    }

    @Override
    public ResponseInterceptor.Chain enrich(ResponseInterceptor.Chain chain) {
      return Mockito.mock(ResponseInterceptor.Chain.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public AsyncClient<Object> enrich(AsyncClient<Object> client) {
      return (AsyncClient<Object>) Mockito.mock(AsyncClient.class);
    }

    @Override
    public Client enrich(Client client) {
      return Mockito.mock(Client.class);
    }

    @Override
    public Contract enrich(Contract contract) {
      return Mockito.mock(Contract.class);
    }

    @Override
    public Decoder enrich(Decoder decoder) {
      return Mockito.mock(Decoder.class);
    }

    @Override
    public ErrorDecoder enrich(ErrorDecoder decoder) {
      return Mockito.mock(ErrorDecoder.class);
    }

    @Override
    public Encoder enrich(Encoder encoder) {
      return Mockito.mock(Encoder.class);
    }

    @Override
    public InvocationHandlerFactory enrich(InvocationHandlerFactory invocationHandlerFactory) {
      return Mockito.mock(InvocationHandlerFactory.class);
    }

    @Override
    public Logger.Level enrich(Logger.Level level) {
      return Mockito.mock(Logger.Level.class);
    }

    @Override
    public Logger enrich(Logger logger) {
      return Mockito.mock(Logger.class);
    }

    @Override
    public MethodInfoResolver enrich(MethodInfoResolver methodInfoResolver) {
      return Mockito.mock(MethodInfoResolver.class);
    }

    @Override
    public Request.Options enrich(Request.Options options) {
      return Mockito.mock(Request.Options.class);
    }

    @Override
    public QueryMapEncoder enrich(QueryMapEncoder queryMapEncoder) {
      return Mockito.mock(QueryMapEncoder.class);
    }

    @Override
    public RequestInterceptor enrich(RequestInterceptor requestInterceptor) {
      return Mockito.mock(RequestInterceptor.class);
    }

    @Override
    public ResponseInterceptor enrich(ResponseInterceptor responseInterceptor) {
      return Mockito.mock(ResponseInterceptor.class);
    }

    @Override
    public Retryer enrich(Retryer retryer) {
      return Mockito.mock(Retryer.class);
    }
  }
}
