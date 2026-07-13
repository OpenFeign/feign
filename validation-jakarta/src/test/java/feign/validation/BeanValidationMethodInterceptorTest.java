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
import feign.Request;
import feign.RequestLine;
import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.lang.reflect.Type;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BeanValidationMethodInterceptorTest {

  private final MockWebServer server = new MockWebServer();

  @BeforeEach
  void setUp() throws IOException {
    server.start();
  }

  @AfterEach
  void tearDown() throws Exception {
    server.close();
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
        .encoders(
            new Encoder() {
              @Override
              public void encode(Object object, Type bodyType, RequestTemplate template)
                  throws EncodeException {
                template.body(Request.Body.of(String.valueOf(object)));
              }

              @Override
              public boolean canEncode(Object object, Type bodyType, RequestTemplate template) {
                return true;
              }
            })
        .methodInterceptor(BeanValidationMethodInterceptor.usingDefaultFactory())
        .target(Api.class, "http://localhost:" + server.getPort());
  }

  @Test
  void validBodyReachesServer() throws Exception {
    server.enqueue(new MockResponse.Builder().body("ok").build());

    String result = api().create(new Payload("a", "b"));

    assertThat(result).isEqualTo("ok");
    assertThat(server.getRequestCount()).isOne();
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
    server.enqueue(new MockResponse.Builder().body("ok").build());

    String result = api().list();

    assertThat(result).isEqualTo("ok");
    assertThat(server.getRequestCount()).isOne();
  }
}
