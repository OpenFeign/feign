/**
 * Copyright 2012-2020 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign;

import static feign.assertj.MockWebServerAssertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Rule;
import org.junit.Test;
import feign.FeignBuilderTest.TestInterface;
import feign.codec.Decoder;
import feign.codec.Encoder;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

public class MethodMetadataPresenceTest {

  @Rule
  public final MockWebServer server = new MockWebServer();

  @Test
  public void client() throws Exception {
    server.enqueue(new MockResponse().setBody("response data"));

    final String url = "http://localhost:" + server.getPort();
    final TestInterface api = Feign.builder()
        .client((request, options) -> {
          assertNotNull(request.requestTemplate());
          assertNotNull(request.requestTemplate().methodMetadata());
          assertNotNull(request.requestTemplate().feignTarget());
          return new Client.Default(null, null).execute(request, options);
        })
        .target(TestInterface.class, url);

    final Response response = api.codecPost("request data");
    assertEquals("response data", Util.toString(response.body().asReader(Util.UTF_8)));

    assertThat(server.takeRequest())
        .hasBody("request data");
  }

  @Test
  public void encoder() throws Exception {
    server.enqueue(new MockResponse().setBody("response data"));

    final String url = "http://localhost:" + server.getPort();
    final TestInterface api = Feign.builder()
        .encoder((object, bodyType, template) -> {
          assertNotNull(template);
          assertNotNull(template.methodMetadata());
          assertNotNull(template.feignTarget());
          new Encoder.Default().encode(object, bodyType, template);
        })
        .target(TestInterface.class, url);

    final Response response = api.codecPost("request data");
    assertEquals("response data", Util.toString(response.body().asReader(Util.UTF_8)));

    assertThat(server.takeRequest())
        .hasBody("request data");
  }

  @Test
  public void decoder() throws Exception {
    server.enqueue(new MockResponse().setBody("response data"));

    final String url = "http://localhost:" + server.getPort();
    final TestInterface api = Feign.builder()
        .decoder((response, type) -> {
          final RequestTemplate template = response.request().requestTemplate();
          assertNotNull(template);
          assertNotNull(template.methodMetadata());
          assertNotNull(template.feignTarget());
          return new Decoder.Default().decode(response, type);
        })
        .target(TestInterface.class, url);

    final Response response = api.codecPost("request data");
    assertEquals("response data", Util.toString(response.body().asReader(Util.UTF_8)));

    assertThat(server.takeRequest())
        .hasBody("request data");
  }

}
