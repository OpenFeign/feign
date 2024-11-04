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
package feign.vertx;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import feign.VertxFeign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.vertx.testcase.HelloServiceAPI;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
@DisplayName("Test that connections does not leak")
class ConnectionsLeakTests {
  private static final HttpServerOptions serverOptions =
      new HttpServerOptions().setLogActivity(true).setPort(8091).setSsl(false);

  HttpServer httpServer;

  private final Set<HttpConnection> connections = new ConcurrentHashSet<>();

  @BeforeEach
  void initServer(Vertx vertx) {
    httpServer = vertx.createHttpServer(serverOptions);
    httpServer.requestHandler(
        request -> {
          if (request.connection() != null) {
            this.connections.add(request.connection());
          }
          request.response().end("Hello world");
        });
    httpServer.listen();
  }

  @AfterEach
  void shutdownServer() {
    httpServer.close();
    connections.clear();
  }

  @Test
  @DisplayName("when use HTTP 1.1")
  void http11NoConnectionLeak(Vertx vertx, VertxTestContext testContext) {
    int pollSize = 3;
    int nbRequests = 100;

    HttpClientOptions options = new HttpClientOptions().setMaxPoolSize(pollSize);

    HelloServiceAPI client =
        VertxFeign.builder()
            .vertx(vertx)
            .options(options)
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .target(HelloServiceAPI.class, "http://localhost:8091");

    assertNotLeaks(client, testContext, nbRequests, pollSize);
  }

  @Test
  @DisplayName("when use HTTP 2")
  void http2NoConnectionLeak(Vertx vertx, VertxTestContext testContext) {
    int pollSize = 1;
    int nbRequests = 100;

    HttpClientOptions options =
        new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_2).setHttp2MaxPoolSize(1);

    HelloServiceAPI client =
        VertxFeign.builder()
            .vertx(vertx)
            .options(options)
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .target(HelloServiceAPI.class, "http://localhost:8091");

    assertNotLeaks(client, testContext, nbRequests, pollSize);
  }

  void assertNotLeaks(
      HelloServiceAPI client, VertxTestContext testContext, int nbRequests, int pollSize) {
    List<Future> futures =
        IntStream.range(0, nbRequests).mapToObj(ignored -> client.hello()).collect(toList());

    CompositeFuture.all(futures)
        .onComplete(
            ignored ->
                testContext.verify(
                    () -> {
                      try {
                        assertThat(this.connections).hasSize(pollSize);
                        testContext.completeNow();
                      } catch (Throwable assertionFailure) {
                        testContext.failNow(assertionFailure);
                      }
                    }));
  }
}
