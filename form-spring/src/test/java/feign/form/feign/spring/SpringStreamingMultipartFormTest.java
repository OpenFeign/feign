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
import feign.Util;
import feign.codec.Encoder;
import feign.form.MultipartFormEncoder;
import feign.form.multipart.MultipartFormBodyFactory;
import feign.form.multipart.PartBodyFactory;
import feign.form.multipart.PartContextResolverChain;
import feign.form.multipart.PartFactory;
import feign.form.spring.MultipartFileEncoder;
import feign.form.spring.MultipartFilePartContextResolver;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;

@SpringBootTest
@WireMockTest(httpPort = 8080)
class SpringStreamingMultipartFormTest {
  private static final String REQUEST_PATH = "/";

  @Autowired private SpringStreamingMultipartFormTestClient testClient;

  @Test
  void shouldSendMultipartFile() {
    var name = "data";
    var filename =
        SpringStreamingMultipartFormTest.class.getSimpleName() + "_shouldSendMultipartFile.txt";
    var contentType = MediaType.TEXT_PLAIN_VALUE;
    var expected = "Hello, World!";
    var multipartFile =
        new MockMultipartFile(
            name, filename, contentType, expected.getBytes(StandardCharsets.UTF_8));

    stubFor(post(REQUEST_PATH).willReturn(ok()));

    testClient.sendMultipartFile(multipartFile);

    verify(
        postRequestedFor(urlPathEqualTo(REQUEST_PATH))
            .withHeader(Util.CONTENT_TYPE, containing(MediaType.MULTIPART_FORM_DATA_VALUE))
            .withRequestBodyPart(
                aMultipart()
                    .withName(name)
                    .withFileName(filename)
                    .withHeader(Util.CONTENT_TYPE, equalTo(contentType))
                    .withBody(equalTo(expected))
                    .build()));
  }

  @FeignClient(name = "spring-streaming-multipart-form-test-client", url = "http://localhost:8080")
  private interface SpringStreamingMultipartFormTestClient {
    @RequestMapping(
        value = REQUEST_PATH,
        method = RequestMethod.POST,
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    void sendMultipartFile(@RequestBody MultipartFile data);
  }

  @Configuration
  private static class SpringStreamingMultipartFormTestConfiguration {
    @Bean
    public Encoder feignEncoder() {
      var partFactory =
          PartFactory.builder()
              .partBodyFactory(new PartBodyFactory(List.of(new MultipartFileEncoder())))
              .build();
      var multipartFormBodyFactory =
          new MultipartFormBodyFactory(
              new PartContextResolverChain(
                  resolvers -> resolvers.addFirst(new MultipartFilePartContextResolver())),
              partFactory);
      return MultipartFormEncoder.builder()
          .multipartFormBodyFactory(multipartFormBodyFactory)
          .build();
    }
  }
}
