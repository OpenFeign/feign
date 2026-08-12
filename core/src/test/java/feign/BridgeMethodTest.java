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

import static feign.assertj.FeignAssertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class BridgeMethodTest {

  interface CrudApi<T> {
    @RequestLine("GET /items/{id}")
    String get(@Param("id") T id);
  }

  interface UserApi extends CrudApi<String> {
    @Override
    @RequestLine("GET /users/{id}")
    String get(@Param("id") String id);
  }

  @Test
  void contractSkipsBridgeMethodsFromGenericOverride() {
    List<MethodMetadata> metadata =
        new Contract.Default().parseAndValidateMetadata(UserApi.class);

    assertThat(metadata).hasSize(1);
    assertThat(metadata.get(0).configKey()).isEqualTo("UserApi#get(String)");
    assertThat(metadata.get(0).template()).hasMethod("GET").hasUrl("/users/{id}");
  }

  @Test
  void callsThroughGenericSuperInterfaceUseBridgedHandler() {
    AtomicReference<Request> captured = new AtomicReference<>();

    CrudApi<String> api =
        (CrudApi<String>)
            Feign.builder()
                .client(
                    (request, options) -> {
                      captured.set(request);
                      return Response.builder()
                          .status(200)
                          .reason("OK")
                          .request(request)
                          .headers(Collections.emptyMap())
                          .body("ok", Util.UTF_8)
                          .build();
                    })
                .target(UserApi.class, "http://localhost:1");

    assertThat(api.get("1")).isEqualTo("ok");
    assertThat(captured.get().url()).isEqualTo("http://localhost:1/users/1");
  }
}
