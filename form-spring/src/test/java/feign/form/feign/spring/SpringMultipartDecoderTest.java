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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@SpringBootTest(
    webEnvironment = DEFINED_PORT,
    classes = Server.class,
    properties = {"server.port=8081", "feign.hystrix.enabled=false"})
class SpringMultipartDecoderTest {

  @Autowired private DownloadClient downloadClient;

  @Test
  void downloadTest() throws Exception {
    MultipartFile[] downloads = downloadClient.download("123");

    assertThat(downloads.length).isEqualTo(2);

    assertThat(downloads[0].getName()).isEqualTo("info");

    MediaType infoContentType = MediaType.parseMediaType(downloads[0].getContentType());
    assertThat(MediaType.TEXT_PLAIN.includes(infoContentType)).isTrue();
    assertThat(infoContentType.getCharset()).isNotNull();
    assertThat(IOUtils.toString(downloads[0].getInputStream(), infoContentType.getCharset().name()))
        .isEqualTo("The text for file ID 123. Testing unicode €");

    assertThat(downloads[1].getOriginalFilename()).isEqualTo("testfile.txt");
    assertThat(downloads[1].getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    assertThat(downloads[1].getSize()).isEqualTo(14);
  }
}
