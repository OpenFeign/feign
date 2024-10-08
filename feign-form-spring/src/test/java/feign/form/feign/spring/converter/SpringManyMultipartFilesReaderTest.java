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
package feign.form.feign.spring.converter;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

import feign.form.spring.converter.SpringManyMultipartFilesReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import lombok.val;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

class SpringManyMultipartFilesReaderTest {

  private static final String DUMMY_MULTIPART_BOUNDARY = "Boundary_4_574237629_1500021738802";

  @Test
  void readMultipartFormDataTest() throws IOException {
    val multipartFilesReader = new SpringManyMultipartFilesReader(4096);
    val multipartFiles =
        multipartFilesReader.read(MultipartFile[].class, new ValidMultipartMessage());

    assertThat(multipartFiles.length).isEqualTo(2);

    assertThat(multipartFiles[0].getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
    assertThat(multipartFiles[0].getName()).isEqualTo("form-item-1");
    assertThat(multipartFiles[0].isEmpty()).isFalse();

    assertThat(multipartFiles[1].getContentType()).isEqualTo(MediaType.TEXT_PLAIN_VALUE);
    assertThat(multipartFiles[1].getOriginalFilename()).isEqualTo("form-item-2-file-1");
    assertThat(IOUtils.toString(multipartFiles[1].getInputStream(), "US-ASCII"))
        .isEqualTo("Plain text");
  }

  static class ValidMultipartMessage implements HttpInputMessage {

    @Override
    public InputStream getBody() throws IOException {
      val multipartBody =
          "--"
              + DUMMY_MULTIPART_BOUNDARY
              + "\r\n"
              + "Content-Type: application/json\r\n"
              + "Content-Disposition: form-data; name=\"form-item-1\"\r\n"
              + "\r\n"
              + "{\"id\":1}"
              + "\r\n"
              + "--"
              + DUMMY_MULTIPART_BOUNDARY
              + "\r\n"
              + "content-type: text/plain\r\n"
              + "content-disposition: Form-Data; Filename=\"form-item-2-file-1\";"
              + " Name=\"form-item-2\"\r\n"
              + "\r\n"
              + "Plain text\r\n"
              + "--"
              + DUMMY_MULTIPART_BOUNDARY
              + "--\r\n";

      return new ByteArrayInputStream(multipartBody.getBytes("US-ASCII"));
    }

    @Override
    public HttpHeaders getHeaders() {
      val httpHeaders = new HttpHeaders();
      httpHeaders.put(
          CONTENT_TYPE,
          singletonList(MULTIPART_FORM_DATA_VALUE + "; boundary=" + DUMMY_MULTIPART_BOUNDARY));
      return httpHeaders;
    }
  }
}
