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
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import feign.Request;

public class RequestKeyTest {

  private RequestKey requestKey;

  @Before
  public void setUp() {
    RequestHeaders headers = RequestHeaders
        .builder()
        .add("my-header", "val").build();
    requestKey =
        RequestKey.builder(HttpMethod.GET, "a").headers(headers).charset(StandardCharsets.UTF_16)
            .body("content").build();
  }

  @Test
  public void builder() throws Exception {
    assertThat(requestKey.getMethod()).isEqualTo(HttpMethod.GET);
    assertThat(requestKey.getUrl()).isEqualTo("a");
    assertThat(requestKey.getHeaders().size()).isEqualTo(1);
    assertThat(requestKey.getHeaders().fetch("my-header"))
        .isEqualTo((Collection<String>) Arrays.asList("val"));
    assertThat(requestKey.getCharset()).isEqualTo(StandardCharsets.UTF_16);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void create() throws Exception {
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("my-header", Arrays.asList("val"));
    Request request =
        Request.create(Request.HttpMethod.GET, "a", map, "content".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_16);
    requestKey = RequestKey.create(request);

    assertThat(requestKey.getMethod()).isEqualTo(HttpMethod.GET);
    assertThat(requestKey.getUrl()).isEqualTo("a");
    assertThat(requestKey.getHeaders().size()).isEqualTo(1);
    assertThat(requestKey.getHeaders().fetch("my-header"))
        .isEqualTo((Collection<String>) Arrays.asList("val"));
    assertThat(requestKey.getCharset()).isEqualTo(StandardCharsets.UTF_16);
    assertThat(requestKey.getBody()).isEqualTo("content".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  public void checkHashes() {
    RequestKey requestKey1 = RequestKey.builder(HttpMethod.GET, "a").build();
    RequestKey requestKey2 = RequestKey.builder(HttpMethod.GET, "b").build();

    assertThat(requestKey1.hashCode()).isNotEqualTo(requestKey2.hashCode());
    assertThat(requestKey1).isNotEqualTo(requestKey2);
  }

  @Test
  public void equalObject() {
    assertThat(requestKey).isNotEqualTo(new Object());
  }

  @Test
  public void equalNull() {
    assertThat(requestKey).isNotEqualTo(null);
  }

  @Test
  public void equalPost() {
    RequestKey requestKey1 = RequestKey.builder(HttpMethod.GET, "a").build();
    RequestKey requestKey2 = RequestKey.builder(HttpMethod.POST, "a").build();

    assertThat(requestKey1.hashCode()).isNotEqualTo(requestKey2.hashCode());
    assertThat(requestKey1).isNotEqualTo(requestKey2);
  }

  @Test
  public void equalSelf() {
    assertThat(requestKey.hashCode()).isEqualTo(requestKey.hashCode());
    assertThat(requestKey).isEqualTo(requestKey);
  }

  @Test
  public void equalMinimum() {
    RequestKey requestKey2 = RequestKey.builder(HttpMethod.GET, "a").build();

    assertThat(requestKey.hashCode()).isEqualTo(requestKey2.hashCode());
    assertThat(requestKey).isEqualTo(requestKey2);
  }

  @Test
  public void equalExtra() {
    RequestHeaders headers = RequestHeaders
        .builder()
        .add("my-other-header", "other value").build();
    RequestKey requestKey2 = RequestKey.builder(HttpMethod.GET, "a").headers(headers)
        .charset(StandardCharsets.ISO_8859_1).build();

    assertThat(requestKey.hashCode()).isEqualTo(requestKey2.hashCode());
    assertThat(requestKey).isEqualTo(requestKey2);
  }

  @Test
  public void equalsExtended() {
    RequestKey requestKey2 = RequestKey.builder(HttpMethod.GET, "a").build();

    assertThat(requestKey.hashCode()).isEqualTo(requestKey2.hashCode());
    assertThat(requestKey.equalsExtended(requestKey2)).isEqualTo(true);
  }

  @Test
  public void equalsExtendedExtra() {
    RequestHeaders headers = RequestHeaders
        .builder()
        .add("my-other-header", "other value").build();
    RequestKey requestKey2 = RequestKey.builder(HttpMethod.GET, "a").headers(headers)
        .charset(StandardCharsets.ISO_8859_1).build();

    assertThat(requestKey.hashCode()).isEqualTo(requestKey2.hashCode());
    assertThat(requestKey.equalsExtended(requestKey2)).isEqualTo(false);
  }

  @Test
  public void testToString() throws Exception {
    assertThat(requestKey.toString()).startsWith("Request [GET a: ");
    assertThat(requestKey.toString(),
        both(containsString(" with my-header=[val] ")).and(containsString(" UTF-16]")));
  }

  @Test
  public void testToStringSimple() throws Exception {
    requestKey = RequestKey.builder(HttpMethod.GET, "a").build();

    assertThat(requestKey.toString()).startsWith("Request [GET a: ");
    assertThat(requestKey.toString(),
        both(containsString(" without ")).and(containsString(" no charset")));
  }

}
//
