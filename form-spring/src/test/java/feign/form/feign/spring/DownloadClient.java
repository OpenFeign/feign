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

import feign.Logger;
import feign.codec.Decoder;
import feign.form.spring.converter.SpringManyMultipartFilesReader;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.support.FeignHttpMessageConverters;
import org.springframework.cloud.openfeign.support.HttpMessageConverterCustomizer;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(
    name = "multipart-download-support-service",
    url = "http://localhost:8080",
    configuration = DownloadClient.ClientConfiguration.class)
interface DownloadClient {

  @RequestMapping("/multipart/download/{fileId}")
  MultipartFile[] download(@PathVariable("fileId") String fileId);

  class ClientConfiguration {

    @Bean
    HttpMessageConverterCustomizer multipartConverterCustomizer() {
      return converters -> converters.add(new SpringManyMultipartFilesReader(4096));
    }

    @Bean
    Decoder feignDecoder(ObjectProvider<FeignHttpMessageConverters> messageConverters) {
      return new SpringDecoder(messageConverters);
    }

    @Bean
    Logger.Level feignLoggerLevel() {
      return Logger.Level.FULL;
    }
  }
}
