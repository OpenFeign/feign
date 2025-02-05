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

import feign.VertxFeign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.vertx.tests.testcase.HelloServiceAPI;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.PoolOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;

@DisplayName("Tests of reconnection with HTTP 2")
class Http2ClientReconnectTest extends AbstractClientReconnectTest {

  @BeforeAll
  @Override
  protected void createClient(Vertx vertx) {
    WebClientOptions options = new WebClientOptions().setProtocolVersion(HttpVersion.HTTP_2);
    PoolOptions poolOptions = new PoolOptions().setHttp2MaxSize(1);
    WebClient webClient = WebClient.create(vertx, options, poolOptions);

    client =
        VertxFeign.builder()
            .webClient(webClient)
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .target(HelloServiceAPI.class, baseUrl);
  }
}
