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
package feign.mock;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import feign.Request;

class RequestKeyTest {

  private RequestKey requestKey;

  @BeforeEach
  void setUp() {
    RequestHeaders headers = RequestHeaders
        .builder()
        .add("my-header", "val").build();
    requestKey =
        RequestKey.builder(HttpMethod.GET, "a").headers(headers).charset(StandardCharsets.UTF_16)
            .body("content").build();
  }

  @Test
  void builder() throws Exception {
    assertThat(requestKey.getMethod()).isEqualTo(HttpMethod.GET);
    assertThat(requestKey.getUrl()).isEqualTo("a");
    assertThat(requestKey.getHeaders().size()).isEqualTo(1);
    assertThat(requestKey.getHeaders().fetch("my-header"))
        .isEqualTo(Arrays.asList("val"));
    assertThat(requestKey.getCharset()).isEqualTo(StandardCharsets.UTF_16);
  }

  @SuppressWarnings("deprecation")
  @Test
  void create() throws Exception {
    Map<String, Collection<String>> map = new HashMap<>();
    map.put("my-header", Arrays.asList("val"));
    Request request =
        Request.create(Request.HttpMethod.GET, "a", map, "content".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_16);
    requestKey = RequestKey.create(request);

    assertThat(requestKey.getMethod()).isEqualTo(HttpMethod.GET);
    assertThat(requestKey.getUrl()).isEqualTo("a");
    assertThat(requestKey.getHeaders().size()).isEqualTo(1);
    assertThat(requestKey.getHeaders().fetch("my-header"))
        .isEqualTo(Arrays.asList("val"));
    assertThat(requestKey.getCharset()).isEqualTo(StandardCharsets.UTF_16);
    assertThat(requestKey.getBody()).isEqualTo("content".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void checkHashes() {
    RequestKey requestKey1 = RequestKey.builder(HttpMethod.GET, "a").build();
    RequestKey requestKey2 = RequestKey.builder(HttpMethod.GET, "b").build();

    assertThat(requestKey1.hashCode()).isNotEqualTo(requestKey2.hashCode());
    assertThat(requestKey1).isNotEqualTo(requestKey2);
  }

  @Test
  void equalObject() {
    assertThat(requestKey).isNotEqualTo(new Object());
  }

  @Test
  void equalNull() {
    assertThat(requestKey).isNotEqualTo(null);
  }

  @Test
  void equalPost() {
    RequestKey requestKey1 = RequestKey.builder(HttpMethod.GET, "a").build();
    RequestKey requestKey2 = RequestKey.builder(HttpMethod.POST, "a").build();

    assertThat(requestKey1.hashCode()).isNotEqualTo(requestKey2.hashCode());
    assertThat(requestKey1).isNotEqualTo(requestKey2);
  }

  @Test
  void equalSelf() {
    assertThat(requestKey.hashCode()).isEqualTo(requestKey.hashCode());
    assertThat(requestKey).isEqualTo(requestKey);
  }

  @Test
  void equalMinimum() {
    RequestKey requestKey2 = RequestKey.builder(HttpMethod.GET, "a").build();

    assertThat(requestKey.hashCode()).isEqualTo(requestKey2.hashCode());
    assertThat(requestKey).isEqualTo(requestKey2);
  }

  @Test
  void equalExtra() {
    RequestHeaders headers = RequestHeaders
        .builder()
        .add("my-other-header", "other value").build();
    RequestKey requestKey2 = RequestKey.builder(HttpMethod.GET, "a").headers(headers)
        .charset(StandardCharsets.ISO_8859_1).build();

    assertThat(requestKey.hashCode()).isEqualTo(requestKey2.hashCode());
    assertThat(requestKey).isEqualTo(requestKey2);
  }

  @Test
  void equalsExtended() {
    RequestKey requestKey2 = RequestKey.builder(HttpMethod.GET, "a").build();

    assertThat(requestKey.hashCode()).isEqualTo(requestKey2.hashCode());
    assertThat(requestKey.equalsExtended(requestKey2)).isEqualTo(true);
  }

  @Test
  void equalsExtendedExtra() {
    RequestHeaders headers = RequestHeaders
        .builder()
        .add("my-other-header", "other value").build();
    RequestKey requestKey2 = RequestKey.builder(HttpMethod.GET, "a").headers(headers)
        .charset(StandardCharsets.ISO_8859_1).build();

    assertThat(requestKey.hashCode()).isEqualTo(requestKey2.hashCode());
    assertThat(requestKey.equalsExtended(requestKey2)).isEqualTo(false);
  }

  @Test
  void testToString() throws Exception {
    assertThat(requestKey.toString()).startsWith("Request [GET a: ");
    assertThat(requestKey.toString()).contains(" with my-header=[val] ", " UTF-16]");
  }

  @Test
  void toStringSimple() throws Exception {
    requestKey = RequestKey.builder(HttpMethod.GET, "a").build();

    assertThat(requestKey.toString()).startsWith("Request [GET a: ");
    assertThat(requestKey.toString()).contains(" without ", " no charset");
  }

}
//
