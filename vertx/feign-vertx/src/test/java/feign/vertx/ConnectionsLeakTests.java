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
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Collections;
import java.util.HashSet;
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

  private final Set<HttpConnection> connections = Collections.synchronizedSet(new HashSet<>());

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
    int poolSize = 3;
    int nbRequests = 100;

    WebClientOptions options = new WebClientOptions();
    PoolOptions poolOptions = new PoolOptions().setHttp1MaxSize(poolSize);
    WebClient webClient = WebClient.create(vertx, options, poolOptions);

    HelloServiceAPI client =
        VertxFeign.builder()
            .webClient(webClient)
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .target(HelloServiceAPI.class, "http://localhost:8091");

    assertNotLeaks(client, testContext, nbRequests, poolSize);
  }

  @Test
  @DisplayName("when use HTTP 2")
  void http2NoConnectionLeak(Vertx vertx, VertxTestContext testContext) {
    int poolSize = 1;
    int nbRequests = 100;

    WebClientOptions options = new WebClientOptions().setProtocolVersion(HttpVersion.HTTP_2);
    PoolOptions poolOptions = new PoolOptions().setHttp2MaxSize(1);
    WebClient webClient = WebClient.create(vertx, options, poolOptions);

    HelloServiceAPI client =
        VertxFeign.builder()
            .webClient(webClient)
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .target(HelloServiceAPI.class, "http://localhost:8091");

    assertNotLeaks(client, testContext, nbRequests, poolSize);
  }

  void assertNotLeaks(
      HelloServiceAPI client, VertxTestContext testContext, int nbRequests, int poolSize) {
    List<Future<?>> futures =
        IntStream.range(0, nbRequests).mapToObj(_ -> client.hello()).collect(toList());

    Future.all(futures)
        .onComplete(
            _ ->
                testContext.verify(
                    () -> {
                      try {
                        assertThat(this.connections).hasSize(poolSize);
                        testContext.completeNow();
                      } catch (Throwable assertionFailure) {
                        testContext.failNow(assertionFailure);
                      }
                    }));
  }
}
