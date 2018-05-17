/**
 * Copyright 2012-2018 The Feign Authors
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
package feign.mock;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class RequestHeadersTest {

  @Test
  public void shouldCreateEmptyRequestHeaders() {
    RequestHeaders headers = RequestHeaders
        .builder()
        .build();
    assertThat(headers.size()).isEqualTo(0);
  }

  @Test
  public void shouldReturnZeroSizeForUnknownKey() {
    RequestHeaders headers = RequestHeaders
        .builder()
        .build();
    assertThat(headers.sizeOf("unknown")).isEqualTo(0);
  }

  @Test
  public void shouldCreateRequestHeadersFromSingleValue() {
    RequestHeaders headers = RequestHeaders
        .builder()
        .add("header", "val")
        .add("other header", "val2")
        .build();

    assertThat(headers.fetch("header")).contains("val");
    assertThat(headers.sizeOf("header")).isEqualTo(1);
    assertThat(headers.fetch("other header")).contains("val2");
    assertThat(headers.sizeOf("other header")).isEqualTo(1);
  }

  @Test
  public void shouldCreateRequestHeadersFromSingleValueAndCollection() {
    RequestHeaders headers = RequestHeaders
        .builder()
        .add("header", "val")
        .add("other header", "val2")
        .add("header", Arrays.asList("val3", "val4"))
        .build();

    assertThat(headers.fetch("header")).contains("val", "val3", "val4");
    assertThat(headers.sizeOf("header")).isEqualTo(3);
    assertThat(headers.fetch("other header")).contains("val2");
    assertThat(headers.sizeOf("other header")).isEqualTo(1);
  }

  @Test
  public void shouldCreateRequestHeadersFromHeadersMap() {
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("header", Arrays.asList("val", "val2"));
    RequestHeaders headers = RequestHeaders.of(map);
    assertThat(headers.size()).isEqualTo(1);
  }

  @Test
  public void shouldPrintHeaders() {
    RequestHeaders headers = RequestHeaders
        .builder()
        .add("header", "val")
        .add("other header", "val2")
        .add("header", Arrays.asList("val3", "val4"))
        .build();
    assertThat(headers.toString()).isEqualTo("other header=[val2], header=[val, val3, val4]");
  }
}
