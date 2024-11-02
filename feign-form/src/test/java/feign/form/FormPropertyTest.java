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
import feign.jackson.JacksonEncoder;
import java.nio.file.Path;
import lombok.val;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = DEFINED_PORT, classes = Server.class)
class FormPropertyTest {

  private static FormClient API;

  @TempDir static Path logDir;

  @BeforeAll
  static void configureClient() {
    val logFile = logDir.resolve("log.txt").toString();

    API =
        Feign.builder()
            .encoder(new FormEncoder(new JacksonEncoder()))
            .logger(new JavaLogger(FormPropertyTest.class).appendToFile(logFile))
            .logLevel(FULL)
            .target(FormClient.class, "http://localhost:8080");
  }

  @Test
  void test() {
    val dto = new FormDto("Amigo", 23);

    assertThat(API.postData(dto)).isEqualTo("Amigo=23");
  }

  interface FormClient {

    @RequestLine("POST /form-data")
    @Headers("Content-Type: application/x-www-form-urlencoded")
    String postData(FormDto dto);
  }
}
