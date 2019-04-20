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

import java.util.ArrayList;

import feign.Logger;
import feign.codec.Decoder;
import feign.form.spring.converter.SpringManyMultipartFilesReader;
import lombok.val;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(
    name = "multipart-download-support-service",
    url = "http://localhost:8081",
    configuration = DownloadClient.ClientConfiguration.class
)
public interface DownloadClient {

  @RequestMapping("/multipart/download/{fileId}")
  MultipartFile[] download (@PathVariable("fileId") String fileId);

  class ClientConfiguration {

    @Autowired
    private ObjectFactory<HttpMessageConverters> messageConverters;

    @Bean
    public Decoder feignDecoder () {
      val springConverters = messageConverters.getObject().getConverters();
      val decoderConverters = new ArrayList<HttpMessageConverter<?>>(springConverters.size() + 1);

      decoderConverters.addAll(springConverters);
      decoderConverters.add(new SpringManyMultipartFilesReader(4096));

      val httpMessageConverters = new HttpMessageConverters(decoderConverters);

      return new SpringDecoder(new ObjectFactory<HttpMessageConverters>() {

        @Override
        public HttpMessageConverters getObject () {
            return httpMessageConverters;
        }
      });
    }

    @Bean
    public Logger.Level feignLoggerLevel () {
      return Logger.Level.FULL;
    }
  }
}
