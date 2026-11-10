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

import static feign.Util.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import feign.Request.Body;
import feign.Request.HttpMethod;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class RequestTest {

  @Test
  void stringBodyIsEncodedWithUtf8NotThePlatformDefault() throws IOException {
    String content = "spécial-çhars-€";

    Body body = Body.of(content);

    assertThat(body.writeToByteArray()).isEqualTo(content.getBytes(UTF_8));
    assertThat(body.writeToString(UTF_8)).isEqualTo(content);
    assertThat(body.contentLength()).isEqualTo(content.getBytes(UTF_8).length);
  }

  @Test
  void requestWithoutBodyHasEmptyBody() {
    Request request = createGetRequestWithNullBody();

    assertThat(request.body()).isEmpty();
  }

  @Test
  void toStringOfRequestWithoutBodyOmitsBodySection() {
    Request request = createGetRequestWithNullBody();

    assertThat(request).hasToString("GET http://example.com HTTP/1.1\n");
  }

  @Test
  void requestBuiltFromTemplateWithoutBodyOmitsBodyFromToString() {
    Request request =
        new RequestTemplate()
            .method(HttpMethod.GET)
            .target("http://example.com")
            .resolve(Collections.emptyMap())
            .request();

    assertThat(request).hasToString("GET http://example.com HTTP/1.1\n");
  }

  @Test
  void headersAreCopiedOnCreation() {
    Map<String, Collection<String>> headers = new LinkedHashMap<>();
    List<String> values = new ArrayList<>(List.of("a"));
    headers.put("X-Header", values);

    Request request = Request.create(HttpMethod.GET, "http://example.com", headers, null, null);
    headers.put("X-Other", List.of("b"));
    values.add("c");

    assertThat(request.headers()).containsOnly(entry("X-Header", List.of("a")));
    assertThatThrownBy(() -> request.headers().put("X-Other", List.of("b")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> request.headers().get("X-Header").add("c"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void withHeaderReturnsCopyAndKeepsCaseInsensitiveLookup() {
    Request request =
        new RequestTemplate()
            .method(HttpMethod.GET)
            .target("http://example.com")
            .header("Accept", "text/plain")
            .resolve(Collections.emptyMap())
            .request();

    Request traced = request.withHeader("traceparent", List.of("00-abc"));

    assertThat(request.headers()).doesNotContainKey("traceparent");
    assertThat(traced.headers().get("TRACEPARENT")).containsExactly("00-abc");
    assertThat(traced.headers().get("accept")).containsExactly("text/plain");
  }

  private static Request createGetRequestWithNullBody() {
    return Request.create(
        HttpMethod.GET, "http://example.com", Collections.emptyMap(), (Body) null, null);
  }
}
