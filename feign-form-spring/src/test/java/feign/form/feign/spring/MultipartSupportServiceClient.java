/*
 * Copyright 2017 Artem Labazin <xxlabaza@gmail.com>.
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

import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;

import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;

/**
 * @author Tomasz Juchniewicz <tjuchniewicz@gmail.com>
 * @since 22.08.2016
 */
@FeignClient(
    name = "multipart-support-service",
    url = "http://localhost:8080",
    configuration = MultipartSupportServiceClient.MultipartSupportConfig.class
)
public interface MultipartSupportServiceClient extends IMultipartSupportService {

    @Configuration
    public class MultipartSupportConfig {

        @Bean
        @Primary
        @Scope("prototype")
        public Encoder feignSpringFormEncoder () {
            return new SpringFormEncoder();
        }
    }
}
