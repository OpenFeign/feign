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
package feign;

import static feign.assertj.MockWebServerAssertions.assertThat;

import feign.FeignBuilderTest.TestInterface;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.core.DefaultClient;
import feign.core.codec.DefaultDecoder;
import feign.core.codec.DefaultEncoder;
import java.io.IOException;
import java.lang.reflect.Type;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MethodMetadataPresenceTest {

  public final MockWebServer server = new MockWebServer();

  @Test
  void client() throws Exception {
    server.enqueue(new MockResponse.Builder().body("response data").build());

    final String url = "http://localhost:" + server.getPort();
    final TestInterface api =
        Feign.builder()
            .client(
                (request, options) -> {
                  assertThat(request.requestTemplate()).isNotNull();
                  assertThat(request.requestTemplate().methodMetadata()).isNotNull();
                  assertThat(request.requestTemplate().feignTarget()).isNotNull();
                  return new DefaultClient(null, null).execute(request, options);
                })
            .target(TestInterface.class, url);

    final Response response = api.codecPost("request data");
    assertThat(Util.toString(response.body().asReader(Util.UTF_8))).isEqualTo("response data");

    assertThat(server.takeRequest()).hasBody("request data");
  }

  @Test
  void encoder() throws Exception {
    server.enqueue(new MockResponse.Builder().body("response data").build());

    final String url = "http://localhost:" + server.getPort();
    final TestInterface api =
        Feign.builder()
            .encoders(
                new Encoder() {
                  @Override
                  public void encode(Object object, Type bodyType, RequestTemplate template)
                      throws EncodeException {
                    assertThat(template).isNotNull();
                    assertThat(template.methodMetadata()).isNotNull();
                    assertThat(template.feignTarget()).isNotNull();
                    new DefaultEncoder().encode(object, bodyType, template);
                  }

                  @Override
                  public boolean canEncode(Object object, Type bodyType, RequestTemplate template) {
                    return true;
                  }
                })
            .target(TestInterface.class, url);

    final Response response = api.codecPost("request data");
    assertThat(Util.toString(response.body().asReader(Util.UTF_8))).isEqualTo("response data");

    assertThat(server.takeRequest()).hasBody("request data");
  }

  @Test
  void decoder() throws Exception {
    server.enqueue(new MockResponse.Builder().body("response data").build());

    final String url = "http://localhost:" + server.getPort();
    final TestInterface api =
        Feign.builder()
            .decoder(
                (response, type) -> {
                  final RequestTemplate template = response.request().requestTemplate();
                  assertThat(template).isNotNull();
                  assertThat(template.methodMetadata()).isNotNull();
                  assertThat(template.feignTarget()).isNotNull();
                  return new DefaultDecoder().decode(response, type);
                })
            .target(TestInterface.class, url);

    final Response response = api.codecPost("request data");
    assertThat(Util.toString(response.body().asReader(Util.UTF_8))).isEqualTo("response data");

    assertThat(server.takeRequest()).hasBody("request data");
  }

  @BeforeEach
  void setUp() throws IOException {
    server.start();
  }

  @AfterEach
  void afterEachTest() throws IOException {
    server.close();
  }
}
