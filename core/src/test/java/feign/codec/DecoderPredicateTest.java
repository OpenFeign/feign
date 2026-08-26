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
package feign.codec;

import static org.assertj.core.api.Assertions.assertThat;

import feign.Request;
import feign.Request.HttpMethod;
import feign.Response;
import feign.Util;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DecoderPredicateTest {

  private static Response response(String contentType) {
    return response(contentType, 200, "body");
  }

  private static Response response(String contentType, int status, String body) {
    Map<String, Collection<String>> headers = new HashMap<>();
    if (contentType != null) {
      headers.put("Content-Type", Collections.singletonList(contentType));
    }
    Response.Builder builder =
        Response.builder()
            .status(status)
            .reason("OK")
            .headers(headers)
            .request(
                Request.create(
                    HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8, null));
    if (body != null) {
      builder.body(body, Util.UTF_8);
    }
    return builder.build();
  }

  @Test
  void jsonContentTypeMatchesPlainAndSuffixedTypes() {
    DecoderPredicate predicate = DecoderPredicate.jsonContentType();

    assertThat(predicate.canDecode(response("application/json"), String.class)).isTrue();
    assertThat(predicate.canDecode(response("application/json;charset=utf-8"), String.class))
        .isTrue();
    assertThat(predicate.canDecode(response("APPLICATION/JSON"), String.class)).isTrue();
    assertThat(predicate.canDecode(response("text/json"), String.class)).isTrue();
    assertThat(predicate.canDecode(response("application/vnd.github+json"), String.class)).isTrue();
    assertThat(predicate.canDecode(response("application/xml"), String.class)).isFalse();
    assertThat(predicate.canDecode(response("application/x-json-stream"), String.class)).isFalse();
    assertThat(predicate.canDecode(response(null), String.class)).isFalse();
  }

  @Test
  void xmlContentTypeMatchesPlainAndSuffixedTypes() {
    DecoderPredicate predicate = DecoderPredicate.xmlContentType();

    assertThat(predicate.canDecode(response("application/xml"), String.class)).isTrue();
    assertThat(predicate.canDecode(response("text/xml;charset=utf-8"), String.class)).isTrue();
    assertThat(predicate.canDecode(response("application/soap+xml"), String.class)).isTrue();
    assertThat(predicate.canDecode(response("application/json"), String.class)).isFalse();
    assertThat(predicate.canDecode(response(null), String.class)).isFalse();
  }

  @Test
  void contentTypeIgnoresCaseAndParameters() {
    DecoderPredicate predicate = DecoderPredicate.contentType("text/csv");

    assertThat(predicate.canDecode(response("text/csv"), String.class)).isTrue();
    assertThat(predicate.canDecode(response("TEXT/CSV;charset=utf-8"), String.class)).isTrue();
    assertThat(predicate.canDecode(response("text/csv-x"), String.class)).isFalse();
    assertThat(predicate.canDecode(response("text/plain"), String.class)).isFalse();
  }

  @Test
  void emptyBodyMatchesResponsesWithoutContent() {
    DecoderPredicate predicate = DecoderPredicate.emptyBody();

    assertThat(predicate.canDecode(response("application/json", 204, null), String.class)).isTrue();
    assertThat(predicate.canDecode(response("application/json", 200, ""), String.class)).isTrue();
    assertThat(predicate.canDecode(response("application/json", 200, "body"), String.class))
        .isFalse();
  }

  @Test
  void statusMatchesTheGivenCodes() {
    DecoderPredicate predicate = DecoderPredicate.status(204, 404);

    assertThat(predicate.canDecode(response("application/json", 204, null), String.class)).isTrue();
    assertThat(predicate.canDecode(response("application/json", 404, null), String.class)).isTrue();
    assertThat(predicate.canDecode(response("application/json", 200, "body"), String.class))
        .isFalse();
  }

  @Test
  void returnTypeMatchesTheExpectedType() {
    DecoderPredicate predicate = DecoderPredicate.returnType(byte[].class);

    assertThat(predicate.canDecode(response("application/octet-stream"), byte[].class)).isTrue();
    assertThat(predicate.canDecode(response("application/octet-stream"), String.class)).isFalse();
  }

  @Test
  void combinesPredicates() {
    DecoderPredicate json = DecoderPredicate.jsonContentType();
    DecoderPredicate ok = DecoderPredicate.status(200);

    assertThat(json.and(ok).canDecode(response("application/json"), String.class)).isTrue();
    assertThat(json.and(ok).canDecode(response("application/json", 204, null), String.class))
        .isFalse();
    assertThat(json.or(ok).canDecode(response("text/plain"), String.class)).isTrue();
    assertThat(json.negate().canDecode(response("text/plain"), String.class)).isTrue();
  }
}
