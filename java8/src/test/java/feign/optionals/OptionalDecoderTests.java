/**
 * Copyright 2012-2018 The Feign Authors
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.optionals;

import static org.assertj.core.api.Assertions.assertThat;

import feign.Feign;
import feign.RequestLine;
import feign.codec.Decoder;
import java.io.IOException;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;

public class OptionalDecoderTests {

  interface OptionalInterface {
    @RequestLine("GET /")
    Optional<String> get();
  }

  @Test
  public void simpleOptionalTest() throws IOException, InterruptedException {
    MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setResponseCode(404));
    server.enqueue(new MockResponse().setBody("foo"));

    OptionalInterface api =
        Feign.builder()
            .decode404()
            .decoder(new OptionalDecoder(new Decoder.Default()))
            .target(OptionalInterface.class, server.url("/").toString());

    assertThat(api.get().isPresent()).isFalse();
    assertThat(api.get().get()).isEqualTo("foo");
  }
}
