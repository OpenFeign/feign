/*
 * Copyright 2015 Netflix, Inc.
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

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import org.junit.Rule;
import org.junit.Test;

import feign.Target.HardCodedTarget;

import static feign.assertj.MockWebServerAssertions.assertThat;

public class TargetTest {

  @Rule
  public final MockWebServer server = new MockWebServer();

  interface TestQuery {

    @RequestLine("GET /{path}?query={query}")
    Response get(@Param("path") String path, @Param("query") String query);
  }

  @Test
  public void baseCaseQueryParamsArePercentEncoded() throws InterruptedException {
    server.enqueue(new MockResponse());

    String baseUrl = server.url("/default").toString();

    Feign.builder().target(TestQuery.class, baseUrl).get("slash/foo", "slash/bar");

    assertThat(server.takeRequest()).hasPath("/default/slash/foo?query=slash%2Fbar");
  }

  /**
   * Per <a href="https://github.com/Netflix/feign/issues/227">#227</a>, some may want to opt out of
   * percent encoding. Here's how.
   */
  @Test
  public void targetCanCreateCustomRequest() throws InterruptedException {
    server.enqueue(new MockResponse());

    String baseUrl = server.url("/default").toString();
    Target<TestQuery> custom = new HardCodedTarget<TestQuery>(TestQuery.class, baseUrl) {

      @Override
      public Request apply(RequestTemplate input) {
        Request urlEncoded = super.apply(input);
        return Request.create(
            urlEncoded.method(),
            urlEncoded.url().replace("%2F", "/"),
            urlEncoded.headers(),
            urlEncoded.body(), urlEncoded.charset()
        );
      }
    };

    Feign.builder().target(custom).get("slash/foo", "slash/bar");

    assertThat(server.takeRequest()).hasPath("/default/slash/foo?query=slash/bar");
  }
}
