package feign.vertx;

import feign.VertxFeign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.vertx.testcase.HelloServiceAPI;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;

@DisplayName("Tests of reconnection with HTTP 1.1")
public class Http11ClientReconnectTest extends AbstractClientReconnectTest {

  @BeforeAll
  @Override
  protected void createClient(final Vertx vertx) {
    HttpClientOptions options = new HttpClientOptions()
        .setMaxPoolSize(3);

    client = VertxFeign
        .builder()
        .vertx(vertx)
        .options(options)
        .encoder(new JacksonEncoder())
        .decoder(new JacksonDecoder())
        .target(HelloServiceAPI.class, baseUrl);
  }
}
