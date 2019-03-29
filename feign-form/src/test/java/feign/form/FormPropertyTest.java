/*
 * Copyright 2019 the original author or authors.
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import feign.Feign;
import feign.Headers;
import feign.RequestLine;
import feign.jackson.JacksonEncoder;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 *
 * @author marembo
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(
        webEnvironment = DEFINED_PORT,
        classes = Server.class
)
public class FormPropertyTest {

  private static final FormClient API;

  static {
    API = Feign.builder()
            .encoder(new FormEncoder(new JacksonEncoder()))
            .logger(new feign.Logger.JavaLogger().appendToFile("log.txt"))
            .logLevel(FULL)
            .target(FormClient.class, "http://localhost:8080");
  }

  @Test
  public void test () {
    val dto = new FormDto("Amigo", 23);
    val stringResponse = API.postData(dto);

    assertNotNull(stringResponse);

    log.info("Response: {}", stringResponse);

    assertEquals("Amigo=23", stringResponse);
  }

  public interface FormClient {

    @RequestLine("POST /form-data")
    @Headers("Content-Type: application/x-www-form-urlencoded")
    String postData (FormDto dto);

  }

}
