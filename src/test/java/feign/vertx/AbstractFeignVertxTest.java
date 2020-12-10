package feign.vertx;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import feign.vertx.testcase.domain.OrderGenerator;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public abstract class AbstractFeignVertxTest {
  protected static WireMockServer wireMock = new WireMockServer(options().dynamicPort());
  protected static final OrderGenerator generator = new OrderGenerator();

  @BeforeAll
  @DisplayName("Setup WireMock server")
  static void setupWireMockServer() {
    wireMock.start();
  }

  @AfterAll
  @DisplayName("Shutdown WireMock server")
  static void shutdownWireMockServer() {
    wireMock.stop();
  }
}
