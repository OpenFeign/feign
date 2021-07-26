/**
 * Copyright 2012-2021 The Feign Authors
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
package feign.micrometer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import org.junit.Test;
import feign.Feign;
import feign.RequestLine;
import feign.Response;
import feign.codec.ErrorDecoder;
import feign.mock.HttpMethod;
import feign.mock.MockClient;
import feign.mock.MockTarget;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public class MeteredFeignClientTest {

  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  private final MockClient mockClient = new MockClient();

  private final TestClient testClient = Feign.builder()
      .client(mockClient)
      .errorDecoder(new TestClientErrorDecoder())
      .addCapability(new MicrometerCapability(meterRegistry))
      .target(new MockTarget<>(TestClient.class));

  @Test
  public void shouldDecodeWithCustomErrorDecoder() {

    mockClient.add(HttpMethod.GET, "/test", 500, "");

    final RuntimeException e = assertThrows(RuntimeException.class, testClient::testMethod);

    assertThat(e.getMessage()).isEqualTo("Test error");

    final Timer timer = meterRegistry.find("feign.Feign.exception").timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1);
  }

  interface TestClient {
    @RequestLine("GET /test")
    void testMethod();
  }


  static class TestClientErrorDecoder implements ErrorDecoder {
    @Override
    public Exception decode(final String methodKey, final Response response) {
      throw new RuntimeException("Test error");
    }
  }
}
