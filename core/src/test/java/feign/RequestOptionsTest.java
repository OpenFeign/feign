/**
 * Copyright 2012-2019 The Feign Authors
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

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

public class RequestOptionsTest {

  private static final int DEFAULT_READ_TIMEOUT = 7 * 1000;
  private static final int CUSTOM_READ_TIMEOUT = 3 * 1000;

  @Rule
  public final MockWebServer server = new MockWebServer();
  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  private HasReadTimeoutApiMethod api;

  @Before
  public void instantiateApi() {
    api = Feign.builder()
        .options(new Request.Options(15 * 1000, DEFAULT_READ_TIMEOUT))
        .target(HasReadTimeoutApiMethod.class, "http://localhost:" + server.getPort());
    server.enqueue(new MockResponse().setBody("5").setBodyDelay(DEFAULT_READ_TIMEOUT - 1 * 1000,
        TimeUnit.MILLISECONDS));
  }

  @Test
  public void testCustomReadTimeOutApplied() {
    thrown.expect(FeignException.class);
    thrown.expectMessage("Read timed out");

    api.withApiTimeout();
  }

  @Test
  public void testDefaultReadTimeOutApplied() {
    String result = api.withoutApiTimeout();
    assertThat(result).isEqualTo("5");
  }


  interface HasReadTimeoutApiMethod {
    @ApiTimeout(readTimeoutMillis = CUSTOM_READ_TIMEOUT)
    @RequestLine("GET /api/withApiTimeout")
    String withApiTimeout();

    @RequestLine("GET /api/withoutApiTimeout")
    String withoutApiTimeout();
  }
}
