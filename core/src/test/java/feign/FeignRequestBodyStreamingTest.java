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
package feign;

import static feign.assertj.MockWebServerAssertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FeignRequestBodyStreamingTest {
  private final MockWebServer mockWebServer = new MockWebServer();
  private TestClient testClient;

  @BeforeEach
  void setup() throws IOException {
    mockWebServer.enqueue(new MockResponse());
    mockWebServer.start();
    testClient = Feign.builder().target(TestClient.class, mockWebServer.url("/").toString());
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  void shouldSendFile(@TempDir File tempDir) throws IOException, InterruptedException {
    var file = new File(tempDir, "FeignRequestBodyStreamingTest_shouldSendFile.txt");
    var expected = "File content";

    Files.writeString(file.toPath(), expected);

    testClient.post(file);

    assertThat(mockWebServer.takeRequest()).hasBody(expected);
  }

  @Test
  void shouldSendPath(@TempDir Path tempDir) throws IOException, InterruptedException {
    var path = tempDir.resolve("FeignRequestBodyStreamingTest_shouldSendPath.txt");
    var expected = "Path content";

    Files.writeString(path, expected);

    testClient.post(path);

    assertThat(mockWebServer.takeRequest()).hasBody(expected);
  }

  @Test
  void shouldSendInputStream() throws IOException, InterruptedException {
    var expected = "InputStream content";

    try (var inputStream = new ByteArrayInputStream(expected.getBytes(StandardCharsets.UTF_8))) {
      testClient.post(inputStream);
    }

    assertThat(mockWebServer.takeRequest()).hasBody(expected);
  }

  @Test
  void shouldSendRequestBody() throws InterruptedException {
    var expected = "Request body content";

    testClient.post(Request.Body.of(expected));

    assertThat(mockWebServer.takeRequest()).hasBody(expected);
  }

  private interface TestClient {
    @RequestLine("POST /")
    void post(File body);

    @RequestLine("POST /")
    void post(Path body);

    @RequestLine("POST /")
    void post(InputStream body);

    @RequestLine("POST /")
    void post(Request.Body body);
  }
}
