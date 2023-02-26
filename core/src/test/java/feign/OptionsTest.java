/*
 * Copyright 2012-2023 The Feign Authors
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

import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author pengfei.zhao
 */
@SuppressWarnings("deprecation")
public class OptionsTest {

  static class ChildOptions extends Request.Options {
    public ChildOptions(int connectTimeoutMillis, int readTimeoutMillis) {
      super(connectTimeoutMillis, readTimeoutMillis);
    }
  }

  interface OptionsInterface {
    @RequestLine("GET /")
    String get(Request.Options options);

    @RequestLine("POST /")
    String getChildOptions(ChildOptions options);

    @RequestLine("GET /")
    String get();
  }

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void socketTimeoutTest() {
    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBody("foo").setBodyDelay(3, TimeUnit.SECONDS));

    final OptionsInterface api = Feign.builder()
        .options(new Request.Options(1000, 1000))
        .target(OptionsInterface.class, server.url("/").toString());

    thrown.expect(FeignException.class);
    thrown.expectCause(CoreMatchers.isA(SocketTimeoutException.class));

    api.get();
  }

  @Test
  public void normalResponseTest() {
    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBody("foo").setBodyDelay(3, TimeUnit.SECONDS));

    final OptionsInterface api = Feign.builder()
        .options(new Request.Options(1000, 1000))
        .target(OptionsInterface.class, server.url("/").toString());

    assertThat(api.get(new Request.Options(1000, 4 * 1000))).isEqualTo("foo");
  }

  @Test
  public void normalResponseForChildOptionsTest() {
    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBody("foo").setBodyDelay(3, TimeUnit.SECONDS));

    final OptionsInterface api = Feign.builder()
        .options(new ChildOptions(1000, 1000))
        .target(OptionsInterface.class, server.url("/").toString());

    assertThat(api.getChildOptions(new ChildOptions(1000, 4 * 1000))).isEqualTo("foo");
  }
}
