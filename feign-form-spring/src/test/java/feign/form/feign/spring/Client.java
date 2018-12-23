/*
 * Copyright 2018 Artem Labazin
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

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;
import java.util.Map;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/**
 *
 * @author Artem Labazin
 */
@FeignClient(
    name = "multipart-support-service",
    url = "http://localhost:8080",
    configuration = Client.ClientConfiguration.class
)
public interface Client {

  @RequestMapping(
      value = "/multipart/upload1/{folder}",
      method = POST,
      consumes = MULTIPART_FORM_DATA_VALUE
  )
  String upload1 (@PathVariable("folder") String folder,
                  @RequestPart MultipartFile file,
                  @RequestParam(value = "message", required = false) String message);

  @RequestMapping(
      value = "/multipart/upload2/{folder}",
      method = POST,
      consumes = MULTIPART_FORM_DATA_VALUE
  )
  String upload2 (@RequestBody MultipartFile file,
                  @PathVariable("folder") String folder,
                  @RequestParam(value = "message", required = false) String message);

  @RequestMapping(
      value = "/multipart/upload3/{folder}",
      method = POST,
      consumes = MULTIPART_FORM_DATA_VALUE
  )
  String upload3 (@RequestBody MultipartFile file,
                  @PathVariable("folder") String folder,
                  @RequestParam(value = "message", required = false) String message);

  @RequestMapping(
      path = "/multipart/upload4/{id}",
      method = POST,
      produces = APPLICATION_JSON_VALUE
  )
  String upload4 (@PathVariable("id") String id,
                  @RequestBody Map<Object, Object> map,
                  @RequestParam("userName") String userName);

  public static class ClientConfiguration {

    @Autowired
    private ObjectFactory<HttpMessageConverters> messageConverters;

    @Bean
    public Encoder feignEncoder () {
      return new SpringFormEncoder(new SpringEncoder(messageConverters));
    }
  }
}
