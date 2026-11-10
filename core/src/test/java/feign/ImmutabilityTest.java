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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ImmutabilityTest {

  interface Api {
    @RequestLine("POST /")
    @Headers("Content-Type: application/x-www-form-urlencoded")
    @Body("name={name}")
    String post(@Param("name") String name);
  }

  private final List<Request> sent = new ArrayList<>();

  private final Client recordingClient =
      (request, options) -> {
        sent.add(request);
        return Response.builder()
            .status(200)
            .request(request)
            .headers(Collections.<String, Collection<String>>emptyMap())
            .body("ok", Util.UTF_8)
            .build();
      };

  @Test
  void builderChangesAfterBuildDoNotLeakIntoBuiltClients() {
    Feign.Builder builder =
        Feign.builder()
            .client(recordingClient)
            .requestInterceptor(template -> template.header("X-First", "1"));
    Api first = builder.target(Api.class, "http://localhost");

    builder.requestInterceptor(template -> template.header("X-Second", "2"));
    first.post("feign");

    assertThat(sent)
        .singleElement()
        .satisfies(
            request -> {
              assertThat(request.headers()).containsKey("X-First");
              assertThat(request.headers()).doesNotContainKey("X-Second");
            });
  }

  @Test
  void methodMetadataIsReadOnlyOnceParsed() {
    AtomicReference<MethodMetadata> metadata = new AtomicReference<>();
    Api api =
        Feign.builder()
            .client(
                (request, options) -> {
                  metadata.set(request.methodMetadata());
                  return recordingClient.execute(request, options);
                })
            .target(Api.class, "http://localhost");

    api.post("feign");

    MethodMetadata parsed = metadata.get();
    assertThat(parsed.configKey()).isEqualTo("Api#post(String)");
    assertThat(sent).singleElement().extracting(Request::feignTarget).isNotNull();
    assertThatThrownBy(() -> parsed.setBodyRequired(false))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> parsed.configKey("other")).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> parsed.formParams().add("other"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> parsed.indexToName().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
