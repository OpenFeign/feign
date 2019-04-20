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

package feign.form.feign.spring;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.multipart.MultipartFile;

@RunWith(SpringRunner.class)
@SpringBootTest(
    webEnvironment = DEFINED_PORT,
    classes = Server.class,
    properties = {
        "server.port=8081",
        "feign.hystrix.enabled=false"
    }
)
public class SpringMultipartDecoderTest {

  @Autowired
  private DownloadClient downloadClient;

  @Test
  public void downloadTest () throws Exception {
    MultipartFile[] downloads = downloadClient.download("123");

    Assert.assertEquals(2, downloads.length);

    Assert.assertEquals("info", downloads[0].getName());
    MediaType infoContentType = MediaType.parseMediaType(downloads[0].getContentType());
    Assert.assertTrue(MediaType.TEXT_PLAIN.includes(infoContentType));
    Assert.assertNotNull(infoContentType.getCharset());
    Assert.assertEquals("The text for file ID 123. Testing unicode €",
            IOUtils.toString(downloads[0].getInputStream(), infoContentType.getCharset().name()));

    Assert.assertEquals("testfile.txt", downloads[1].getOriginalFilename());
    Assert.assertEquals(MediaType.APPLICATION_OCTET_STREAM_VALUE, downloads[1].getContentType());
    Assert.assertEquals(14, downloads[1].getSize());
  }
}
