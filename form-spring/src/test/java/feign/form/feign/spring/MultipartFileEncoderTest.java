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

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import feign.codec.Encoder;
import feign.form.spring.MultipartFileEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;

@SpringBootTest
@WireMockTest(httpPort = 8080)
public class MultipartFileEncoderTest {
  private static final String REQUEST_PATH = "/";

  @Autowired private StreamingMultipartFileTestClient testClient;

  @BeforeEach
  void setUp() {
    stubFor(post(REQUEST_PATH).willReturn(ok()));
  }

  @Test
  void shouldSendMultipartFile() {
    var name = "data";
    var filename = "shouldSendMultipartFile.txt";
    var contentType = MediaType.TEXT_PLAIN_VALUE;
    var expected = "Hello, World!";
    var multipartFile =
        new MockMultipartFile(
            name, filename, contentType, expected.getBytes(StandardCharsets.UTF_8));

    testClient.sendMultipartFile(multipartFile);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart()
                    .withName(name)
                    .withFileName(filename)
                    .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(contentType))
                    .withBody(equalTo(expected))
                    .build()));
  }

  @Test
  void shouldSendMultipartFileArray() {
    var name1 = "data1";
    var name2 = "data2";
    var filename1 = "shouldSendMultipartFileArray1.txt";
    var filename2 = "shouldSendMultipartFileArray2.txt";
    var contentType = MediaType.TEXT_PLAIN_VALUE;
    var expected1 = "expected1";
    var expected2 = "expected2";
    var data =
        new MultipartFile[] {
          new MockMultipartFile(
              name1, filename1, contentType, expected1.getBytes(StandardCharsets.UTF_8)),
          new MockMultipartFile(
              name2, filename2, contentType, expected2.getBytes(StandardCharsets.UTF_8))
        };

    testClient.sendMultipartFileArray(data);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart()
                    .withName(name1)
                    .withFileName(filename1)
                    .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(contentType))
                    .withBody(equalTo(expected1))
                    .build())
            .withRequestBodyPart(
                aMultipart()
                    .withName(name2)
                    .withFileName(filename2)
                    .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(contentType))
                    .withBody(equalTo(expected2))
                    .build()));
  }

  @Test
  void shouldSendMultipartFileCollection() {
    var name1 = "data1";
    var name2 = "data2";
    var filename1 = "shouldSendMultipartFileArray1.txt";
    var filename2 = "shouldSendMultipartFileArray2.txt";
    var contentType = MediaType.TEXT_PLAIN_VALUE;
    var expected1 = "expected1";
    var expected2 = "expected2";
    var data =
        List.<MultipartFile>of(
            new MockMultipartFile(
                name1, filename1, contentType, expected1.getBytes(StandardCharsets.UTF_8)),
            new MockMultipartFile(
                name2, filename2, contentType, expected2.getBytes(StandardCharsets.UTF_8)));

    testClient.sendMultipartFileCollection(data);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart()
                    .withName(name1)
                    .withFileName(filename1)
                    .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(contentType))
                    .withBody(equalTo(expected1))
                    .build())
            .withRequestBodyPart(
                aMultipart()
                    .withName(name2)
                    .withFileName(filename2)
                    .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(contentType))
                    .withBody(equalTo(expected2))
                    .build()));
  }

  @FeignClient(name = "streamingMultipartFileTestClient", url = "http://localhost:8080")
  private interface StreamingMultipartFileTestClient {
    @RequestMapping(
        value = REQUEST_PATH,
        method = RequestMethod.POST,
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    void sendMultipartFile(@RequestBody MultipartFile data);

    @RequestMapping(
        value = REQUEST_PATH,
        method = RequestMethod.POST,
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    void sendMultipartFileArray(@RequestBody MultipartFile[] data);

    @RequestMapping(
        value = REQUEST_PATH,
        method = RequestMethod.POST,
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    void sendMultipartFileCollection(@RequestBody Collection<MultipartFile> data);
  }

  @Configuration
  private static class SpringStreamingMultipartFormTestConfiguration {
    @Bean
    public Encoder feignEncoder() {
      return new MultipartFileEncoder();
    }
  }
}
