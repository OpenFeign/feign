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
import feign.Param;
import feign.RequestLine;
import feign.Response;
import feign.jackson.JacksonEncoder;
import java.nio.file.Path;
import lombok.val;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = DEFINED_PORT, classes = Server.class)
class ByteArrayClientTest {

  private static CustomClient API;

  @TempDir static Path logDir;

  @BeforeAll
  static void configureClient() {
    val encoder = new FormEncoder(new JacksonEncoder());
    val logFile = logDir.resolve("log-byte.txt").toString();

    API =
        Feign.builder()
            .encoder(encoder)
            .logger(new JavaLogger(ByteArrayClientTest.class).appendToFile(logFile))
            .logLevel(FULL)
            .target(CustomClient.class, "http://localhost:8080");
  }

  @Test
  void testNotTreatedAsFileUpload() {
    byte[] bytes = "Hello World".getBytes();

    assertThat(API.uploadByteArray(bytes)).isNotNull().extracting(Response::status).isEqualTo(200);
  }

  interface CustomClient {

    @RequestLine("POST /upload/byte_array_parameter")
    @Headers("Content-Type: multipart/form-data")
    Response uploadByteArray(@Param("file") byte[] bytes);
  }
}
