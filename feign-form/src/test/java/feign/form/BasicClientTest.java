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
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

import feign.Feign;
import feign.jackson.JacksonEncoder;
import lombok.val;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author Artem Labazin
 * @since 30.04.2016
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
    webEnvironment = DEFINED_PORT,
    classes = Server.class
)
public class BasicClientTest {

  private static final TestClient API;

  static {
    API = Feign.builder()
        .encoder(new FormEncoder(new JacksonEncoder()))
        .logger(new feign.Logger.JavaLogger().appendToFile("log.txt"))
        .logLevel(FULL)
        .target(TestClient.class, "http://localhost:8080");
  }

  @Test
  public void testForm () {
    val response = API.form("1", "1");

    Assert.assertNotNull(response);
    Assert.assertEquals(200, response.status());
  }

  @Test
  public void testFormException () {
    val response = API.form("1", "2");

    Assert.assertNotNull(response);
    Assert.assertEquals(400, response.status());
  }

  @Test
  public void testUpload () throws Exception {
    val path = Paths.get(Thread.currentThread().getContextClassLoader().getResource("file.txt").toURI());
    Assert.assertTrue(Files.exists(path));

    val stringResponse = API.upload(path.toFile());
    Assert.assertEquals(Files.size(path), Long.parseLong(stringResponse));
  }

  @Test
  public void testUploadWithParam () throws Exception {
    val path = Paths.get(Thread.currentThread().getContextClassLoader().getResource("file.txt").toURI());
    Assert.assertTrue(Files.exists(path));

    val stringResponse = API.upload(10, Boolean.TRUE, path.toFile());
    Assert.assertEquals(Files.size(path), Long.parseLong(stringResponse));
  }

  @Test
  public void testJson () {
    val dto = new Dto("Artem", 11);
    val stringResponse = API.json(dto);

    Assert.assertEquals("ok", stringResponse);
  }

  @Test
  public void testQueryMap () {
    Map<String, Object> value = singletonMap("filter", (Object) asList("one", "two", "three", "four"));

    val stringResponse = API.queryMap(value);
    Assert.assertEquals("4", stringResponse);
  }

  @Test
  public void testMultipleFilesArray () throws Exception {
    val path1 = Paths.get(Thread.currentThread().getContextClassLoader().getResource("file.txt").toURI());
    Assert.assertTrue(Files.exists(path1));
    val path2 = Paths.get(Thread.currentThread().getContextClassLoader().getResource("another_file.txt").toURI());
    Assert.assertTrue(Files.exists(path2));

    val stringResponse = API.uploadWithArray(new File[] { path1.toFile(), path2.toFile() });
    Assert.assertEquals(Files.size(path1) + Files.size(path2), Long.parseLong(stringResponse));
  }

  @Test
  public void testMultipleFilesList () throws Exception {
    val path1 = Paths.get(Thread.currentThread().getContextClassLoader().getResource("file.txt").toURI());
    Assert.assertTrue(Files.exists(path1));
    val path2 = Paths.get(Thread.currentThread().getContextClassLoader().getResource("another_file.txt").toURI());
    Assert.assertTrue(Files.exists(path2));

    val stringResponse = API.uploadWithList(asList(path1.toFile(), path2.toFile()));
    Assert.assertEquals(Files.size(path1) + Files.size(path2), Long.parseLong(stringResponse));
  }

  @Test
  public void testUploadWithDto () throws Exception {
    val dto = new Dto("Artem", 11);

    val path = Paths.get(Thread.currentThread().getContextClassLoader().getResource("file.txt").toURI());
    Assert.assertTrue(Files.exists(path));

    val response = API.uploadWithDto(dto, path.toFile());
    Assert.assertNotNull(response);
    Assert.assertEquals(200, response.status());
  }

  @Test
  public void testUnknownTypeFile () throws Exception {
    val path = Paths.get(Thread.currentThread().getContextClassLoader().getResource("file.abc").toURI());
    Assert.assertTrue(Files.exists(path));

    val stringResponse = API.uploadUnknownType(path.toFile());
    Assert.assertEquals("application/octet-stream", stringResponse);
  }

  @Test
  public void testFormData () throws Exception {
    val formData = new FormData("application/custom-type", "popa.txt", "Allo".getBytes("UTF-8"));
    val stringResponse = API.uploadFormData(formData);
    Assert.assertEquals("popa.txt:application/custom-type", stringResponse);
  }

  @Test
  public void testSubmitRepeatableQueryParam () throws Exception {
    val names = new String[] { "Milada", "Thais" };
    val stringResponse = API.submitRepeatableQueryParam(names);
    assertThat(stringResponse).isEqualTo("Milada and Thais");
  }

  @Test
  public void testSubmitRepeatableFormParam () throws Exception {
    val names = Arrays.asList("Milada", "Thais");
    val stringResponse = API.submitRepeatableFormParam(names);
    assertThat(stringResponse).isEqualTo("Milada and Thais");
  }
}
