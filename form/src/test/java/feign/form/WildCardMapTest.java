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
package feign.form;

import static feign.Logger.Level.FULL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import feign.Feign;
import feign.Headers;
import feign.Logger.JavaLogger;
import feign.RequestLine;
import feign.Response;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import lombok.val;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = DEFINED_PORT, classes = Server.class)
class WildCardMapTest {

  private static FormUrlEncodedApi api;

  @TempDir static Path logDir;

  @BeforeAll
  static void configureClient() {
    val logFile = logDir.resolve("log.txt").toString();

    api =
        Feign.builder()
            .encoder(new FormEncoder())
            .logger(new JavaLogger(WildCardMapTest.class).appendToFile(logFile))
            .logLevel(FULL)
            .target(FormUrlEncodedApi.class, "http://localhost:8080");
  }

  @Test
  void testOk() {
    Map<String, Object> param =
        new HashMap<String, Object>() {

          private static final long serialVersionUID = 3109256773218160485L;

          {
            put("key1", "1");
            put("key2", "1");
          }
        };

    assertThat(api.wildCardMap(param)).isNotNull().extracting(Response::status).isEqualTo(200);
  }

  @Test
  void testBadRequest() {
    Map<String, Object> param =
        new HashMap<String, Object>() {

          private static final long serialVersionUID = 3109256773218160485L;

          {
            put("key1", "1");
            put("key2", "2");
          }
        };

    assertThat(api.wildCardMap(param)).isNotNull().extracting(Response::status).isEqualTo(418);
  }

  interface FormUrlEncodedApi {

    @RequestLine("POST /wild-card-map")
    @Headers("Content-Type: application/x-www-form-urlencoded")
    Response wildCardMap(Map<String, ?> param);
  }
}
