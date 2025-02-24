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
