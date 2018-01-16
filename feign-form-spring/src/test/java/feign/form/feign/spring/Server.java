/*
 * Copyright 2017 Artem Labazin <xxlabaza@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package feign.form.feign.spring;

import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import java.nio.charset.Charset;
import java.util.Map;

import lombok.SneakyThrows;
import org.apache.commons.codec.CharEncoding;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author Tomasz Juchniewicz <tjuchniewicz@gmail.com>
 * @since 22.08.2016
 */
@RestController
@EnableFeignClients
@SpringBootApplication
public class Server {

  @RequestMapping(
      value = "/multipart/upload1/{folder}",
      method = POST,
      consumes = MULTIPART_FORM_DATA_VALUE
  )
  @SneakyThrows
  public String upload1 (@PathVariable("folder") String folder,
                         @RequestPart MultipartFile file,
                         @RequestParam(value = "message", required = false) String message
  ) {
    return new String(file.getBytes()) + ':' + message + ':' + folder;
  }

  @RequestMapping(
      value = "/multipart/upload2/{folder}",
      method = POST,
      consumes = MULTIPART_FORM_DATA_VALUE
  )
  @SneakyThrows
  public String upload2 (@RequestBody MultipartFile file,
                         @PathVariable("folder") String folder,
                         @RequestParam(value = "message", required = false) String message
  ) {
    return new String(file.getBytes()) + ':' + message + ':' + folder;
  }

  @RequestMapping(
      value = "/multipart/upload3/{folder}",
      method = POST,
      consumes = MULTIPART_FORM_DATA_VALUE
  )
  public String upload3 (@RequestBody MultipartFile file,
                         @PathVariable("folder") String folder,
                         @RequestParam(value = "message", required = false) String message
  ) {
    return file.getOriginalFilename() + ':' + file.getContentType() + ':' + folder;
  }

  @RequestMapping(
      path = "/multipart/upload4/{id}",
      method = POST
  )
  public String upload4 (@PathVariable("id") String id,
                         @RequestBody Map<String, Object> map,
                         @RequestParam String userName
  ) {
    return userName + ':' + id + ':' + map.size();
  }

  @RequestMapping(
          value = "/multipart/download/{fileId}",
          method = GET,
          produces = MULTIPART_FORM_DATA_VALUE
  )
  public MultiValueMap<String, Object> download(@PathVariable("fileId") String fileId) {
    MultiValueMap<String, Object> multiParts = new LinkedMultiValueMap<String, Object>();

    String info = "The text for file ID " + fileId + ". Testing unicode €";
    HttpHeaders infoPartheader = new HttpHeaders();
    infoPartheader.setContentType(new MediaType("text", "plain", Charset.forName(CharEncoding.UTF_8)));
    HttpEntity<String> infoPart = new HttpEntity<String>(info, infoPartheader);

    ClassPathResource file = new ClassPathResource("testfile.txt");
    HttpHeaders filePartheader = new HttpHeaders();
    filePartheader.setContentType(APPLICATION_OCTET_STREAM);
    HttpEntity<ClassPathResource> filePart = new HttpEntity<ClassPathResource>(file, filePartheader);

    multiParts.add("info", infoPart);
    multiParts.add("file", filePart);
    return multiParts;
  }
}
