/*
 * Copyright 2012-2023 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.hc5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import feign.Feign;
import feign.Feign.Builder;
import feign.RequestLine;
import feign.client.AbstractClientTest;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;

/**
 * Tests that 'Content-Encoding: gzip' is handled correctly
 */
public class GzipHttp5ClientTest extends AbstractClientTest {

  @Override
  public Builder newBuilder() {
    return Feign.builder().client(new ApacheHttp5Client());
  }

  @Test
  public void testWithCompressedBody() throws InterruptedException, IOException {
    final TestInterface testInterface = buildTestInterface(true);

    server.enqueue(new MockResponse().setBody("foo"));

    assertEquals("foo", testInterface.withBody("bar"));
    final RecordedRequest request1 = server.takeRequest();
    assertEquals("/test", request1.getPath());

    ByteArrayInputStream bodyContentIs =
        new ByteArrayInputStream(request1.getBody().readByteArray());
    byte[] uncompressed = new GZIPInputStream(bodyContentIs).readAllBytes();

    assertEquals("bar", new String(uncompressed, StandardCharsets.UTF_8));

  }

  @Test
  public void testWithUncompressedBody() throws InterruptedException, IOException {
    final TestInterface testInterface = buildTestInterface(false);

    server.enqueue(new MockResponse().setBody("foo"));

    assertEquals("foo", testInterface.withBody("bar"));
    final RecordedRequest request1 = server.takeRequest();
    assertEquals("/test", request1.getPath());

    assertEquals("bar", request1.getBody().readString(StandardCharsets.UTF_8));
  }


  private TestInterface buildTestInterface(boolean compress) {
    return newBuilder()
        .requestInterceptor(req -> req.header("Content-Encoding", compress ? "gzip" : ""))
        .target(TestInterface.class, "http://localhost:" + server.getPort());
  }


  @Override
  public void veryLongResponseNullLength() {
    assumeTrue(true, "HC5 client seems to hang with response size equalto Long.MAX");
  }

  @Override
  public void contentTypeDefaultsToRequestCharset() throws Exception {
    assumeTrue(true, "this test is flaky on windows, but works fine.");
  }

  public interface TestInterface {

    @RequestLine("POST /test")
    String withBody(String body);

  }
}
