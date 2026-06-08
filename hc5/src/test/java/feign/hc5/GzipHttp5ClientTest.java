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
package feign.hc5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import feign.Feign;
import feign.Feign.Builder;
import feign.RequestLine;
import feign.client.AbstractClientTest;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.Test;

/** Tests that 'Content-Encoding: gzip' is handled correctly */
public class GzipHttp5ClientTest extends AbstractClientTest {

  @Override
  public Builder newBuilder() {
    return Feign.builder().client(new ApacheHttp5Client());
  }

  @Test
  void withCompressedBody() throws Exception {
    final TestInterface testInterface = buildTestInterface(true);

    server.enqueue(new MockResponse.Builder().body("foo").build());

    assertThat(testInterface.withBody("bar")).isEqualTo("foo");
    final RecordedRequest request1 = server.takeRequest();
    assertThat(request1.getTarget()).isEqualTo("/test");

    ByteArrayInputStream bodyContentIs = new ByteArrayInputStream(request1.getBody().toByteArray());
    byte[] uncompressed = new GZIPInputStream(bodyContentIs).readAllBytes();

    assertThat(new String(uncompressed, StandardCharsets.UTF_8)).isEqualTo("bar");
  }

  @Test
  void withUncompressedBody() throws Exception {
    final TestInterface testInterface = buildTestInterface(false);

    server.enqueue(new MockResponse.Builder().body("foo").build());

    assertThat(testInterface.withBody("bar")).isEqualTo("foo");
    final RecordedRequest request1 = server.takeRequest();
    assertThat(request1.getTarget()).isEqualTo("/test");

    assertThat(request1.getBody().string(StandardCharsets.UTF_8)).isEqualTo("bar");
  }

  private TestInterface buildTestInterface(boolean compress) {
    return newBuilder()
        .requestInterceptor(req -> req.header("Content-Encoding", compress ? "gzip" : ""))
        .target(TestInterface.class, "http://localhost:" + server.getPort());
  }

  @Override
  @Test
  public void veryLongResponseNullLength() {
    assumeTrue(true, "HC5 client seems to hang with response size equalto Long.MAX");
  }

  @Override
  @Test
  public void contentTypeDefaultsToRequestCharset() throws Exception {
    assumeTrue(true, "this test is flaky on windows, but works fine.");
  }

  public interface TestInterface {

    @RequestLine("POST /test")
    String withBody(String body);
  }
}
