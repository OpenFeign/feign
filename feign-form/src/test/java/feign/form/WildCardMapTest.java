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
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import java.util.HashMap;
import java.util.Map;

import feign.Feign;
import feign.Headers;
import feign.Logger;
import feign.RequestLine;
import feign.Response;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    webEnvironment = DEFINED_PORT,
    classes = Server.class
)
public class WildCardMapTest {

  private static FormUrlEncodedApi api;

  @BeforeClass
  public static void configureClient () {
    api = Feign.builder()
        .encoder(new FormEncoder())
        .logger(new Logger.JavaLogger().appendToFile("log.txt"))
        .logLevel(FULL)
        .target(FormUrlEncodedApi.class, "http://localhost:8080");
  }

  @Test
  public void testOk () {
    Map<String, Object> param = new HashMap<String, Object>() {

        private static final long serialVersionUID = 3109256773218160485L;

        {
          put("key1", "1");
          put("key2", "1");
        }
    };
    Response response = api.wildCardMap(param);
    Assert.assertEquals(200, response.status());
  }

  @Test
  public void testBadRequest () {
    Map<String, Object> param = new HashMap<String, Object>() {

        private static final long serialVersionUID = 3109256773218160485L;

        {

          put("key1", "1");
          put("key2", "2");
        }
    };
    Response response = api.wildCardMap(param);
    Assert.assertEquals(418, response.status());
  }

  interface FormUrlEncodedApi {

    @RequestLine("POST /wild-card-map")
    @Headers("Content-Type: application/x-www-form-urlencoded")
    Response wildCardMap (Map<String, ?> param);
  }
}
