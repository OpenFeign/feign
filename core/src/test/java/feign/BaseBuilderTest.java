/*
 * Copyright 2012-2023 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.RETURNS_MOCKS;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.Test;
import org.mockito.Mockito;

public class BaseBuilderTest {

  @Test
  public void checkEnrichTouchesAllAsyncBuilderFields()
      throws IllegalArgumentException, IllegalAccessException {
    test(AsyncFeign.builder().requestInterceptor(template -> {
    }), 14);
  }

  private void test(BaseBuilder<?> builder, int expectedFieldsCount)
      throws IllegalArgumentException, IllegalAccessException {
    Capability mockingCapability = Mockito.mock(Capability.class, RETURNS_MOCKS);
    BaseBuilder<?> enriched = builder.addCapability(mockingCapability).enrich();

    List<Field> fields = enriched.getFieldsToEnrich();
    assertThat(fields).hasSize(expectedFieldsCount);

    for (Field field : fields) {
      field.setAccessible(true);
      Object mockedValue = field.get(enriched);
      if (mockedValue instanceof List) {
        mockedValue = ((List<Object>) mockedValue).get(0);
      }
      assertTrue("Field was not enriched " + field, Mockito.mockingDetails(mockedValue)
          .isMock());
    }

  }

  @Test
  public void checkEnrichTouchesAllBuilderFields()
      throws IllegalArgumentException, IllegalAccessException {
    test(Feign.builder().requestInterceptor(template -> {
    }), 12);
  }

}
