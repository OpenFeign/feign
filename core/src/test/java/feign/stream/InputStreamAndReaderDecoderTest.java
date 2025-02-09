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
package feign.stream;

import static org.assertj.core.api.Assertions.assertThat;

import feign.Feign;
import feign.RequestLine;
import feign.Util;
import java.io.InputStream;
import java.io.Reader;
import java.util.Random;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.jupiter.api.Test;

public class InputStreamAndReaderDecoderTest {
  public final MockWebServer server = new MockWebServer();

  interface LargeStreamTestInterface {

    @RequestLine("GET /")
    InputStream getLargeStream();

    @RequestLine("GET /")
    Reader getLargeReader();
  }

  @Test
  void streamingResponse() throws Exception {
    byte[] expectedResponse = new byte[16184];
    new Random().nextBytes(expectedResponse);
    server.enqueue(new MockResponse().setBody(new Buffer().write(expectedResponse)));

    LargeStreamTestInterface api =
        Feign.builder()
            .decoder(new InputStreamAndReaderDecoder(null))
            .target(LargeStreamTestInterface.class, "http://localhost:" + server.getPort());

    try (InputStream is = api.getLargeStream()) {
      byte[] out = is.readAllBytes();
      assertThat(out.length).isEqualTo(expectedResponse.length);
      assertThat(out).isEqualTo(expectedResponse);
    }
  }

  @Test
  void streamingReaderResponse() throws Exception {
    String expectedResponse =
        new Random()
            .ints(1, 1500 + 1)
            .limit(16184)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();

    server.enqueue(
        new MockResponse()
            .setBody(new Buffer().write(expectedResponse.getBytes(Util.UTF_8)))
            .addHeader("content-type", "text/plan; charset=utf-8"));

    LargeStreamTestInterface api =
        Feign.builder()
            .decoder(new InputStreamAndReaderDecoder(null))
            .target(LargeStreamTestInterface.class, "http://localhost:" + server.getPort());

    try (Reader r = api.getLargeReader()) {
      String out = Util.toString(r);
      assertThat(out.length()).isEqualTo(expectedResponse.length());
      assertThat(out).isEqualTo(expectedResponse);
    }
  }

  @Test
  void streamingReaderResponseWithNoCharset() throws Exception {
    String expectedResponse =
        new Random()
            .ints(1, 1500 + 1)
            .limit(16184)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();

    server.enqueue(
        new MockResponse()
            .setBody(new Buffer().write(expectedResponse.getBytes(Util.UTF_8)))
            .addHeader("content-type", "text/plan"));

    LargeStreamTestInterface api =
        Feign.builder()
            .decoder(new InputStreamAndReaderDecoder(null))
            .target(LargeStreamTestInterface.class, "http://localhost:" + server.getPort());

    try (Reader r = api.getLargeReader()) {
      String out = Util.toString(r);
      assertThat(out.length()).isEqualTo(expectedResponse.length());
      assertThat(out).isEqualTo(expectedResponse);
    }
  }
}
