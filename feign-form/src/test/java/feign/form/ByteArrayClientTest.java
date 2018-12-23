/*
 * Copyright 2018 Artem Labazin
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

import feign.Feign;
import feign.Headers;
import feign.Param;
import feign.RequestLine;
import feign.Response;
import feign.jackson.JacksonEncoder;
import lombok.val;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static feign.Logger.Level.FULL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(
    webEnvironment = DEFINED_PORT,
    classes = Server.class
)
public class ByteArrayClientTest {

  private static final CustomClient API;

  static {
    val encoder = new FormEncoder(new JacksonEncoder());

    API = Feign.builder()
        .encoder(encoder)
        .logger(new feign.Logger.JavaLogger().appendToFile("log-byte.txt"))
        .logLevel(FULL)
        .target(CustomClient.class, "http://localhost:8080");
  }

  @Test
  public void testNotTreatedAsFileUpload() {
    byte[] bytes = "Hello World".getBytes();
    val response = API.uploadByteArray(bytes);

    assertNotNull(response);
    assertEquals(200, response.status());
  }

  public interface CustomClient {

    @RequestLine("POST /upload/byte_array_parameter")
    @Headers("Content-Type: multipart/form-data")
    Response uploadByteArray(@Param("file") byte[] bytes);
  }
}
