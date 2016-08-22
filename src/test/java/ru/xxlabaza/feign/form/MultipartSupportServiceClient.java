package ru.xxlabaza.feign.form;

import feign.codec.Encoder;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;

@FeignClient(
    name = "multipart-support-service",
    url = "http://localhost:8080",
    configuration = MultipartSupportServiceClient.MultipartSupportConfig.class)
public interface MultipartSupportServiceClient extends IMultipartSupportService {

  @Configuration
  public class MultipartSupportConfig {

    @Bean
    @Primary
    @Scope("prototype")
    public Encoder feignFormEncoder() {
      return new FormEncoder();
    }
  }
}
