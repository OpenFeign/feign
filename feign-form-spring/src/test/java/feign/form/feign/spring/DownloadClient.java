
package feign.form.feign.spring;

import feign.codec.Decoder;
import feign.form.spring.converter.SpringManyMultipartFilesReader;
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

import java.util.ArrayList;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

import lombok.val;

@FeignClient(
    name = "multipart-download-support-service",
    url = "http://localhost:8080",
    configuration = DownloadClient.ClientConfiguration.class
)
public interface DownloadClient {

    @RequestMapping(
            value = "/multipart/download/{fileId}",
            method = GET
    )
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
    }
}
