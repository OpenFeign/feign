package feign.vertx;

import feign.VertxFeign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.vertx.testcase.HelloServiceAPI;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;

@DisplayName("Tests of reconnection with HTTP 2")
public class Http2ClientReconnectTest extends AbstractClientReconnectTest {

  @BeforeAll
  @Override
  protected void createClient(Vertx vertx) {
    HttpClientOptions options = new HttpClientOptions()
        .setProtocolVersion(HttpVersion.HTTP_2)
        .setHttp2MaxPoolSize(1);

    client = VertxFeign
        .builder()
        .vertx(vertx)
        .options(options)
        .encoder(new JacksonEncoder())
        .decoder(new JacksonDecoder())
        .target(HelloServiceAPI.class, baseUrl);
  }
}
