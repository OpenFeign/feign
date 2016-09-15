/*
 * Copyright 2016 Artem Labazin <xxlabaza@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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

import feign.Feign;
import feign.Headers;
import feign.Param;
import feign.QueryMap;
import feign.RequestLine;
import feign.Response;
import feign.jackson.JacksonEncoder;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import lombok.val;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author Artem Labazin <xxlabaza@gmail.com>
 * @since 30.04.2016
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
    webEnvironment = DEFINED_PORT,
    classes = Server.class,
    properties = {"server.port=8080", "feign.hystrix.enabled=false"})
public class BasicClientTest {

  private static final TestApi api;

  static {
    api =
        Feign.builder()
            .encoder(new FormEncoder(new JacksonEncoder()))
            .logger(new feign.Logger.JavaLogger().appendToFile("log.txt"))
            .logLevel(FULL)
            .target(TestApi.class, "http://localhost:8080");
  }

  @Test
  public void testForm() {
    val response = api.form("1", "1");

    Assert.assertNotNull(response);
    Assert.assertEquals(200, response.status());
  }

  @Test
  public void testFormException() {
    val response = api.form("1", "2");

    Assert.assertNotNull(response);
    Assert.assertEquals(400, response.status());
  }

  @Test
  public void testUpload() throws Exception {
    val path = Paths.get(this.getClass().getClassLoader().getResource("file.txt").toURI());
    Assert.assertTrue(Files.exists(path));

    val stringResponse = api.upload(10, Boolean.TRUE, path.toFile());
    Assert.assertEquals(Files.size(path), Long.parseLong(stringResponse));
  }

  @Test
  public void testJson() {
    val dto = new Dto("Artem", 11);
    val stringResponse = api.json(dto);

    Assert.assertEquals("ok", stringResponse);
  }

  @Test
  public void testQueryMap() {
    Map<String, Object> value =
        Collections.singletonMap("filter", Arrays.asList("one", "two", "three", "four"));

    val stringResponse = api.queryMap(value);
    Assert.assertEquals("4", stringResponse);
  }

  interface TestApi {

    @RequestLine("POST /form")
    @Headers("Content-Type: application/x-www-form-urlencoded")
    Response form(@Param("key1") String key1, @Param("key2") String key2);

    @RequestLine("POST /upload/{id}")
    @Headers("Content-Type: multipart/form-data")
    String upload(
        @Param("id") Integer id, @Param("public") Boolean isPublic, @Param("file") File file);

    @RequestLine("POST /json")
    @Headers("Content-Type: application/json")
    String json(Dto dto);

    @RequestLine("POST /query_map")
    String queryMap(@QueryMap Map<String, Object> value);
  }
}
