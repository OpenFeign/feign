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

import java.io.IOException;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author Tomasz Juchniewicz <tjuchniewicz@gmail.com>
 * @since 22.08.2016
 */
@SpringBootApplication
@RestController
@EnableFeignClients
public class MultipartSupportService implements IMultipartSupportService {

  @Override
  public String upload1(String folder, MultipartFile file, String message) {
    try {
      return new String(file.getBytes()) + ":" + message;
    } catch (IOException e) {
      throw new RuntimeException("Can't get file content", e);
    }
  }

  @Override
  public String upload2(MultipartFile file, String folder, String message) {
    try {
      return new String(file.getBytes()) + ":" + message;
    } catch (IOException e) {
      throw new RuntimeException("Can't get file content", e);
    }
  }
}
