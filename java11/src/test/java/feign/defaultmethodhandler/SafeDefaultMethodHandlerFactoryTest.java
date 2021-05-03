/**
 * Copyright 2012-2021 The Feign Authors
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
package feign.defaultmethodhandler;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import feign.Feign;
import feign.Request;
import feign.RequestLine;
import feign.Response;
import feign.safedefaultmethodhandler.SafeDefaultMethodHandlerFactory;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

public class SafeDefaultMethodHandlerFactoryTest {

  @Rule
  public final MockWebServer server = new MockWebServer();

  @Test
  public void testBasicDefaultMethod() {
    String url = "http://localhost:" + server.getPort();

    TestInterface api = Feign.builder()
        .defaultMethodHandlerFactory(new SafeDefaultMethodHandlerFactory())
        .target(TestInterface.class, url);
    String result = api.independentDefaultMethod();

    assertThat(result.equals("default result")).isTrue();
  }

  @Test
  public void testNoFollowRedirect() {
    server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", "/"));

    String url = "http://localhost:" + server.getPort();
    TestInterface noFollowApi = Feign.builder()
        .defaultMethodHandlerFactory(new SafeDefaultMethodHandlerFactory())
        .options(new Request.Options(100, TimeUnit.MILLISECONDS, 600, TimeUnit.MILLISECONDS, false))
        .target(TestInterface.class, url);

    Response response = noFollowApi.defaultMethodPassthrough();
    assertThat(response.status()).isEqualTo(302);
    assertThat(response.headers().getOrDefault("Location", null))
        .isNotNull()
        .isEqualTo(Collections.singletonList("/"));

    server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", "/"));
    server.enqueue(new MockResponse().setResponseCode(200));
    TestInterface defaultApi = Feign.builder()
        .defaultMethodHandlerFactory(new SafeDefaultMethodHandlerFactory())
        .options(new Request.Options(100, TimeUnit.MILLISECONDS, 600, TimeUnit.MILLISECONDS, true))
        .target(TestInterface.class, url);
    assertThat(defaultApi.defaultMethodPassthrough().status()).isEqualTo(200);
  }

  interface TestInterface {
    @RequestLine("GET")
    Response getNoPath();

    default String independentDefaultMethod() {
      return "default result";
    }

    default Response defaultMethodPassthrough() {
      return getNoPath();
    }
  }

}
