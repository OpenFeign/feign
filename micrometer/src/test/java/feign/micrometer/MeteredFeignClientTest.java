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
