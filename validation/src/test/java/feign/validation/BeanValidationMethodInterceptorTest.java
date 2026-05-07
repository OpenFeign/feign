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
package feign.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import feign.Feign;
import feign.Param;
import feign.RequestLine;
import javax.validation.ConstraintViolationException;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BeanValidationMethodInterceptorTest {

  private final MockWebServer server = new MockWebServer();

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  static class Payload {
    @NotNull public String name;

    @NotBlank public String description;

    Payload(String name, String description) {
      this.name = name;
      this.description = description;
    }

    @Override
    public String toString() {
      return "{\"name\":\"" + name + "\",\"description\":\"" + description + "\"}";
    }
  }

  static class HeaderInfo {
    @NotBlank public String tenant;

    HeaderInfo(String tenant) {
      this.tenant = tenant;
    }
  }

  interface Api {
    @RequestLine("POST /things")
    String create(Payload body);

    @RequestLine("GET /things/{id}")
    String fetch(@Param("id") String id, @Valid HeaderInfo info);

    @RequestLine("GET /things")
    String list();
  }

  private Api api() {
    return Feign.builder()
        .encoder((object, bodyType, template) -> template.body(String.valueOf(object)))
        .methodInterceptor(BeanValidationMethodInterceptor.usingDefaultFactory())
        .target(Api.class, "http://localhost:" + server.getPort());
  }

  @Test
  void validBodyReachesServer() throws Exception {
    server.enqueue(new MockResponse().setBody("ok"));

    String result = api().create(new Payload("a", "b"));

    assertThat(result).isEqualTo("ok");
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  void invalidBodyThrowsBeforeRequest() {
    Payload bad = new Payload(null, "");

    assertThatThrownBy(() -> api().create(bad))
        .isInstanceOf(ConstraintViolationException.class)
        .satisfies(
            ex -> {
              ConstraintViolationException cve = (ConstraintViolationException) ex;
              assertThat(cve.getConstraintViolations()).hasSize(2);
            });
    assertThat(server.getRequestCount()).isZero();
  }

  @Test
  void invalidNonBodyValidParameterThrowsBeforeRequest() {
    HeaderInfo bad = new HeaderInfo("");

    assertThatThrownBy(() -> api().fetch("42", bad))
        .isInstanceOf(ConstraintViolationException.class);
    assertThat(server.getRequestCount()).isZero();
  }

  @Test
  void noArgsMethodPassesThrough() throws Exception {
    server.enqueue(new MockResponse().setBody("ok"));

    String result = api().list();

    assertThat(result).isEqualTo("ok");
    assertThat(server.getRequestCount()).isEqualTo(1);
  }
}
