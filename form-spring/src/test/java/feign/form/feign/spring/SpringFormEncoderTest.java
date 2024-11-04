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
package feign.form.feign.spring;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import feign.Response;
import java.util.HashMap;
import java.util.List;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@SpringBootTest(
    webEnvironment = DEFINED_PORT,
    classes = Server.class,
    properties = {
      "server.port=8080",
      "feign.hystrix.enabled=false",
      "logging.level.feign.form.feign.spring.Client=DEBUG"
    })
class SpringFormEncoderTest {

  @Autowired private Client client;

  @Test
  void upload1Test() throws Exception {
    val folder = "test_folder";
    val file = new MockMultipartFile("file", "test".getBytes(UTF_8));
    val message = "message test";

    assertThat(client.upload1(folder, file, message))
        .isEqualTo(new String(file.getBytes()) + ':' + message + ':' + folder);
  }

  @Test
  void upload2Test() throws Exception {
    val folder = "test_folder";
    val file = new MockMultipartFile("file", "test".getBytes(UTF_8));
    val message = "message test";

    assertThat(client.upload2(file, folder, message))
        .isEqualTo(new String(file.getBytes()) + ':' + message + ':' + folder);
  }

  @Test
  void uploadFileNameAndContentTypeTest() throws Exception {
    val folder = "test_folder";
    val file =
        new MockMultipartFile(
            "file", "hello.dat", "application/octet-stream", "test".getBytes(UTF_8));
    val message = "message test";

    assertThat(client.upload3(file, folder, message))
        .isEqualTo(file.getOriginalFilename() + ':' + file.getContentType() + ':' + folder);
  }

  @Test
  void upload4Test() throws Exception {
    val map = new HashMap<Object, Object>();
    map.put("one", 1);
    map.put("two", 2);

    val userName = "popa";
    val id = "42";

    assertThat(client.upload4(id, map, userName)).isEqualTo(userName + ':' + id + ':' + map.size());
  }

  @Test
  void upload5Test() throws Exception {
    val file = new MockMultipartFile("popa.txt", "Hello world".getBytes(UTF_8));
    val dto = new Dto("field 1 value", 42, file);

    assertThat(client.upload5(dto)).isNotNull().extracting(Response::status).isEqualTo(200);
  }

  @Test
  void upload6ArrayTest() throws Exception {
    val file1 = new MockMultipartFile("popa1", "popa1", null, "Hello".getBytes(UTF_8));
    val file2 = new MockMultipartFile("popa2", "popa2", null, " world".getBytes(UTF_8));

    assertThat(client.upload6Array(new MultipartFile[] {file1, file2})).isEqualTo("Hello world");
  }

  @Test
  void upload6CollectionTest() throws Exception {
    List<MultipartFile> list =
        asList(
            (MultipartFile) new MockMultipartFile("popa1", "popa1", null, "Hello".getBytes(UTF_8)),
            (MultipartFile)
                new MockMultipartFile("popa2", "popa2", null, " world".getBytes(UTF_8)));

    assertThat(client.upload6Collection(list)).isEqualTo("Hello world");
  }
}
