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
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import feign.Feign;
import feign.Logger.JavaLogger;
import feign.Response;
import feign.jackson.JacksonEncoder;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import lombok.val;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = DEFINED_PORT, classes = Server.class)
class BasicClientTest {

  private static TestClient API;

  @TempDir static Path logDir;

  @BeforeAll
  static void configureClient() {
    val logFile = logDir.resolve("log.txt").toString();

    API =
        Feign.builder()
            .encoder(new FormEncoder(new JacksonEncoder()))
            .logger(new JavaLogger(BasicClientTest.class).appendToFile(logFile))
            .logLevel(FULL)
            .target(TestClient.class, "http://localhost:8080");
  }

  @Test
  void testForm() {
    assertThat(API.form("1", "1")).isNotNull().extracting(Response::status).isEqualTo(200);
  }

  @Test
  void testFormException() {
    assertThat(API.form("1", "2")).isNotNull().extracting(Response::status).isEqualTo(400);
  }

  @Test
  void testUpload() throws Exception {
    val path =
        Path.of(Thread.currentThread().getContextClassLoader().getResource("file.txt").toURI());
    assertThat(path).exists();

    assertThat(API.upload(path.toFile())).asLong().isEqualTo(Files.size(path));
  }

  @Test
  void testUploadWithParam() throws Exception {
    val path =
        Path.of(Thread.currentThread().getContextClassLoader().getResource("file.txt").toURI());
    assertThat(path).exists();

    assertThat(API.upload(10, Boolean.TRUE, path.toFile())).asLong().isEqualTo(Files.size(path));
  }

  @Test
  void testJson() {
    val dto = new Dto("Artem", 11);

    assertThat(API.json(dto)).isEqualTo("ok");
  }

  @Test
  void testQueryMap() {
    Map<String, Object> value =
        singletonMap("filter", (Object) asList("one", "two", "three", "four"));

    assertThat(API.queryMap(value)).isEqualTo("4");
  }

  @Test
  void testMultipleFilesArray() throws Exception {
    val path1 =
        Path.of(Thread.currentThread().getContextClassLoader().getResource("file.txt").toURI());
    assertThat(path1).exists();

    val path2 =
        Path.of(
            Thread.currentThread().getContextClassLoader().getResource("another_file.txt").toURI());
    assertThat(path2).exists();

    assertThat(API.uploadWithArray(new File[] {path1.toFile(), path2.toFile()}))
        .asLong()
        .isEqualTo(Files.size(path1) + Files.size(path2));
  }

  @Test
  void testMultipleFilesList() throws Exception {
    val path1 =
        Path.of(Thread.currentThread().getContextClassLoader().getResource("file.txt").toURI());
    assertThat(path1).exists();

    val path2 =
        Path.of(
            Thread.currentThread().getContextClassLoader().getResource("another_file.txt").toURI());
    assertThat(path2).exists();

    assertThat(API.uploadWithList(asList(path1.toFile(), path2.toFile())))
        .asLong()
        .isEqualTo(Files.size(path1) + Files.size(path2));
  }

  @Test
  void testUploadWithDto() throws Exception {
    val dto = new Dto("Artem", 11);

    val path =
        Path.of(Thread.currentThread().getContextClassLoader().getResource("file.txt").toURI());
    assertThat(path).exists();

    assertThat(API.uploadWithDto(dto, path.toFile()))
        .isNotNull()
        .extracting(Response::status)
        .isEqualTo(200);
  }

  @Test
  void testUnknownTypeFile() throws Exception {
    val path =
        Path.of(Thread.currentThread().getContextClassLoader().getResource("file.abc").toURI());
    assertThat(path).exists();

    assertThat(API.uploadUnknownType(path.toFile())).isEqualTo("application/octet-stream");
  }

  @Test
  void testFormData() throws Exception {
    val formData = new FormData("application/custom-type", "popa.txt", "Allo".getBytes("UTF-8"));

    assertThat(API.uploadFormData(formData)).isEqualTo("popa.txt:application/custom-type");
  }

  @Test
  void testSubmitRepeatableQueryParam() throws Exception {
    val names = new String[] {"Milada", "Thais"};
    val stringResponse = API.submitRepeatableQueryParam(names);
    assertThat(stringResponse).isEqualTo("Milada and Thais");
  }

  @Test
  void testSubmitRepeatableFormParam() throws Exception {
    val names = Arrays.asList("Milada", "Thais");
    val stringResponse = API.submitRepeatableFormParam(names);
    assertThat(stringResponse).isEqualTo("Milada and Thais");
  }
}
