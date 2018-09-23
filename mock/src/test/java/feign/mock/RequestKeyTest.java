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

import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;
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
    assertThat(requestKey.getMethod(), equalTo(HttpMethod.GET));
    assertThat(requestKey.getUrl(), equalTo("a"));
    assertThat(requestKey.getHeaders().size(), is(1));
    assertThat(requestKey.getHeaders().fetch("my-header"),
        equalTo((Collection<String>) Arrays.asList("val")));
    assertThat(requestKey.getCharset(), equalTo(StandardCharsets.UTF_16));
  }

  @Test
  public void create() throws Exception {
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("my-header", Arrays.asList("val"));
    Request request =
        Request.create(Request.HttpMethod.GET, "a", map, "content".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_16);
    requestKey = RequestKey.create(request);

    assertThat(requestKey.getMethod(), equalTo(HttpMethod.GET));
    assertThat(requestKey.getUrl(), equalTo("a"));
    assertThat(requestKey.getHeaders().size(), is(1));
    assertThat(requestKey.getHeaders().fetch("my-header"),
        equalTo((Collection<String>) Arrays.asList("val")));
    assertThat(requestKey.getCharset(), equalTo(StandardCharsets.UTF_16));
    assertThat(requestKey.getBody(), equalTo("content".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  public void checkHashes() {
    RequestKey requestKey1 = RequestKey.builder(HttpMethod.GET, "a").build();
    RequestKey requestKey2 = RequestKey.builder(HttpMethod.GET, "b").build();

    assertThat(requestKey1.hashCode(), not(equalTo(requestKey2.hashCode())));
    assertThat(requestKey1, not(equalTo(requestKey2)));
  }

  @Test
  public void equalObject() {
    assertThat(requestKey, not(equalTo(new Object())));
  }

  @Test
  public void equalNull() {
    assertThat(requestKey, not(equalTo(null)));
  }

  @Test
  public void equalPost() {
    RequestKey requestKey1 = RequestKey.builder(HttpMethod.GET, "a").build();
    RequestKey requestKey2 = RequestKey.builder(HttpMethod.POST, "a").build();

    assertThat(requestKey1.hashCode(), not(equalTo(requestKey2.hashCode())));
    assertThat(requestKey1, not(equalTo(requestKey2)));
  }

  @Test
  public void equalSelf() {
    assertThat(requestKey.hashCode(), equalTo(requestKey.hashCode()));
    assertThat(requestKey, equalTo(requestKey));
  }

  @Test
  public void equalMinimum() {
    RequestKey requestKey2 = RequestKey.builder(HttpMethod.GET, "a").build();

    assertThat(requestKey.hashCode(), equalTo(requestKey2.hashCode()));
    assertThat(requestKey, equalTo(requestKey2));
  }

  @Test
  public void equalExtra() {
    RequestHeaders headers = RequestHeaders
        .builder()
        .add("my-other-header", "other value").build();
    RequestKey requestKey2 = RequestKey.builder(HttpMethod.GET, "a").headers(headers)
        .charset(StandardCharsets.ISO_8859_1).build();

    assertThat(requestKey.hashCode(), equalTo(requestKey2.hashCode()));
    assertThat(requestKey, equalTo(requestKey2));
  }

  @Test
  public void equalsExtended() {
    RequestKey requestKey2 = RequestKey.builder(HttpMethod.GET, "a").build();

    assertThat(requestKey.hashCode(), equalTo(requestKey2.hashCode()));
    assertThat(requestKey.equalsExtended(requestKey2), equalTo(true));
  }

  @Test
  public void equalsExtendedExtra() {
    RequestHeaders headers = RequestHeaders
        .builder()
        .add("my-other-header", "other value").build();
    RequestKey requestKey2 = RequestKey.builder(HttpMethod.GET, "a").headers(headers)
        .charset(StandardCharsets.ISO_8859_1).build();

    assertThat(requestKey.hashCode(), equalTo(requestKey2.hashCode()));
    assertThat(requestKey.equalsExtended(requestKey2), equalTo(false));
  }

  @Test
  public void testToString() throws Exception {
    assertThat(requestKey.toString(), startsWith("Request [GET a: "));
    assertThat(requestKey.toString(),
        both(containsString(" with my-header=[val] ")).and(containsString(" UTF-16]")));
  }

  @Test
  public void testToStringSimple() throws Exception {
    requestKey = RequestKey.builder(HttpMethod.GET, "a").build();

    assertThat(requestKey.toString(), startsWith("Request [GET a: "));
    assertThat(requestKey.toString(),
        both(containsString(" without ")).and(containsString(" no charset")));
  }

}
//
