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

package feign.form;

import static feign.Logger.Level.FULL;
import static feign.form.ContentType.MULTIPART;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import feign.Feign;
import feign.Headers;
import feign.Param;
import feign.RequestLine;
import feign.codec.EncodeException;
import feign.form.multipart.ByteArrayWriter;
import feign.form.multipart.Output;
import feign.jackson.JacksonEncoder;
import lombok.val;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author Artem Labazin
 * @since 27.11.2017
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
    webEnvironment = DEFINED_PORT,
    classes = Server.class
)
public class CustomClientTest {

  private static final CustomClient API;

  static {
    val encoder = new FormEncoder(new JacksonEncoder());
    val processor = (MultipartFormContentProcessor) encoder.getContentProcessor(MULTIPART);
    processor.addFirstWriter(new CustomByteArrayWriter());

    API = Feign.builder()
        .encoder(encoder)
        .logger(new feign.Logger.JavaLogger().appendToFile("log.txt"))
        .logLevel(FULL)
        .target(CustomClient.class, "http://localhost:8080");
  }

  @Test
  public void test () {
    val stringResponse = API.uploadByteArray(new byte[0]);

    assertNotNull(stringResponse);
    assertEquals("popa.txt", stringResponse);
  }

  private static class CustomByteArrayWriter extends ByteArrayWriter {

    @Override
    protected void write (Output output, String key, Object value) throws EncodeException {
      writeFileMetadata(output, key, "popa.txt", null);

      val bytes = (byte[]) value;
      output.write(bytes);
    }
  }

  public interface CustomClient {

    @RequestLine("POST /upload/byte_array")
    @Headers("Content-Type: multipart/form-data")
    String uploadByteArray (@Param("file") byte[] bytes);
  }
}
