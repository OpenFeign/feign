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
package feign.mock;

import static org.assertj.core.api.Assertions.assertThat;

import feign.Request;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Request#1552: matching the headers of an expected request must be possible without having to
 * enumerate every header the client generates.
 */
class RequestKeyHeaderSubsetTest {

  private static RequestKey key(RequestHeaders headers, boolean subset) {
    RequestKey.Builder builder = RequestKey.builder(HttpMethod.GET, "a").headers(headers);
    if (subset) {
      builder.matchHeadersBySubset();
    }
    return builder.build();
  }

  /** the key of an incoming request, built exactly as MockClient builds it */
  private static RequestKey incoming(Map<String, Collection<String>> headers) {
    Request request =
        Request.create(Request.HttpMethod.GET, "a", headers, null, StandardCharsets.UTF_16, null);
    return RequestKey.create(request);
  }

  private static Map<String, Collection<String>> headersWith(String... pairs) {
    Map<String, Collection<String>> headers = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      headers.computeIfAbsent(pairs[i], key -> new java.util.ArrayList<>()).add(pairs[i + 1]);
    }
    return headers;
  }

  @Test
  void extraHeadersAreIgnoredWhenMatchingBySubset() {
    RequestHeaders expected = RequestHeaders.builder().add("my-header", "val").build();
    RequestKey actual =
        incoming(headersWith("my-header", "val", "Content-Length", "42", "Host", "localhost"));

    assertThat(key(expected, true).equalsExtended(actual)).isTrue();
  }

  @Test
  void exactMatchingIsPreservedByDefault() {
    RequestHeaders expected = RequestHeaders.builder().add("my-header", "val").build();
    RequestKey actual = incoming(headersWith("my-header", "val", "Content-Length", "42"));

    // unchanged behaviour for callers that did not opt in
    assertThat(key(expected, false).equalsExtended(actual)).isFalse();
  }

  @Test
  void subsetMatchingStillRequiresTheDeclaredHeader() {
    RequestHeaders expected = RequestHeaders.builder().add("my-header", "val").build();
    RequestKey actual = incoming(headersWith("other-header", "other"));

    assertThat(key(expected, true).equalsExtended(actual)).isFalse();
  }

  @Test
  void subsetMatchingStillRequiresTheDeclaredValue() {
    RequestHeaders expected = RequestHeaders.builder().add("my-header", "val").build();
    RequestKey actual = incoming(headersWith("my-header", "different", "Content-Length", "42"));

    assertThat(key(expected, true).equalsExtended(actual)).isFalse();
  }

  @Test
  void allDeclaredValuesMustBePresent() {
    RequestHeaders expected =
        RequestHeaders.builder().add("accept", "text/plain").add("accept", "text/html").build();
    RequestKey missingOne = incoming(headersWith("accept", "text/plain"));
    RequestKey both = incoming(headersWith("accept", "text/plain", "accept", "text/html"));

    assertThat(key(expected, true).equalsExtended(missingOne)).isFalse();
    assertThat(key(expected, true).equalsExtended(both)).isTrue();
  }

  @Test
  void noDeclaredHeadersMatchesAnyRequest() {
    RequestHeaders none = RequestHeaders.builder().build();
    RequestKey actual = incoming(headersWith("Content-Length", "42"));

    assertThat(key(none, true).equalsExtended(actual)).isTrue();
  }

  @Test
  void methodAndUrlAreStillCompared() {
    RequestHeaders expected = RequestHeaders.builder().add("my-header", "val").build();
    RequestKey otherUrl =
        RequestKey.builder(HttpMethod.POST, "a").headers(expected).matchHeadersBySubset().build();

    assertThat(key(expected, true).equalsExtended(otherUrl)).isFalse();
  }
}
