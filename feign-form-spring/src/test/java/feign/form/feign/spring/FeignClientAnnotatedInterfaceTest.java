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
package feign.form.feign.spring;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author Tomasz Juchniewicz <tjuchniewicz@gmail.com>
 * @since 22.08.2016
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
    webEnvironment = DEFINED_PORT,
    classes = MultipartSupportService.class,
    properties = {"server.port=8080", "feign.hystrix.enabled=false"})
public class FeignClientAnnotatedInterfaceTest {

  @Autowired private MultipartSupportServiceClient client;

  @Test
  public void upload1Test() throws Exception {
    MultipartFile file = new MockMultipartFile("file", "test".getBytes(UTF_8));
    String response = client.upload1("test folder", file, "message text");

    Assert.assertEquals("test:message text", response);
  }

  @Test
  public void upload2Test() throws Exception {
    MultipartFile file = new MockMultipartFile("file", "test".getBytes(UTF_8));
    String response = client.upload2(file, "test folder", "message text");

    Assert.assertEquals("test:message text", response);
  }
}
