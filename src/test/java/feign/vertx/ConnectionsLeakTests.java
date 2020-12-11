package feign.vertx;

import feign.VertxFeign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.vertx.testcase.HelloServiceAPI;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@DisplayName("Test that connections does not leak")
public class ConnectionsLeakTests {
  private static final HttpServerOptions serverOptions = new HttpServerOptions()
      .setLogActivity(true)
      .setPort(8091)
      .setSsl(false);

  HttpServer httpServer;

  private final Set<HttpConnection> connections = new ConcurrentHashSet<>();

  @BeforeEach
  public void initServer(Vertx vertx) {
    httpServer = vertx.createHttpServer(serverOptions);
    httpServer.requestHandler(request -> {
      if (request.connection() != null) {
        this.connections.add(request.connection());
      }
      request.response().end("Hello world");
    });
    httpServer.listen();
  }

  @AfterEach
  public void shutdownServer() {
    httpServer.close();
    connections.clear();
  }

  @Test
  @DisplayName("when use HTTP 1.1")
  public void testHttp11NoConnectionLeak(Vertx vertx, VertxTestContext testContext) {
    int pollSize = 3;
    int nbRequests = 100;

    HttpClientOptions options = new HttpClientOptions()
        .setMaxPoolSize(pollSize);

    HelloServiceAPI client = VertxFeign
        .builder()
        .vertx(vertx)
        .options(options)
        .encoder(new JacksonEncoder())
        .decoder(new JacksonDecoder())
        .target(HelloServiceAPI.class, "http://localhost:8091");

    assertNotLeaks(client, testContext, nbRequests, pollSize);
  }

  @Test
  @DisplayName("when use HTTP 2")
  public void testHttp2NoConnectionLeak(Vertx vertx, VertxTestContext testContext) {
    int pollSize = 1;
    int nbRequests = 100;

    HttpClientOptions options = new HttpClientOptions()
        .setProtocolVersion(HttpVersion.HTTP_2)
        .setHttp2MaxPoolSize(1);

    HelloServiceAPI client = VertxFeign
            .builder()
            .vertx(vertx)
            .options(options)
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .target(HelloServiceAPI.class, "http://localhost:8091");

    assertNotLeaks(client, testContext, nbRequests, pollSize);
  }

  void assertNotLeaks(HelloServiceAPI client, VertxTestContext testContext, int nbRequests, int pollSize) {
    List<Future> futures = IntStream
        .range(0, nbRequests)
        .mapToObj(ignored -> client.hello())
        .collect(toList());

    CompositeFuture
        .all(futures)
        .onComplete(ignored -> testContext.verify(() -> {
          try {
            assertThat(this.connections.size())
                .isEqualTo(pollSize);
            testContext.completeNow();
          } catch (Throwable assertionFailure) {
            testContext.failNow(assertionFailure);
          }
        }));
  }
}
