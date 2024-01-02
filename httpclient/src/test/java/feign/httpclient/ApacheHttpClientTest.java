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
package feign.httpclient;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.nio.charset.StandardCharsets;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.jupiter.api.Test;
import feign.Feign;
import feign.Feign.Builder;
import feign.FeignException;
import feign.Request.Options;
import feign.client.AbstractClientTest;
import feign.jaxrs.JAXRSContract;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;

/**
 * Tests client-specific behavior, such as ensuring Content-Length is sent when specified.
 */
public class ApacheHttpClientTest extends AbstractClientTest {

  @Override
  public Builder newBuilder() {
    return Feign.builder().client(new ApacheHttpClient());
  }

  @Test
  void queryParamsAreRespectedWhenBodyIsEmpty() throws InterruptedException {
    final JaxRsTestInterface testInterface = buildTestInterface();

    server.enqueue(new MockResponse().setBody("foo"));
    server.enqueue(new MockResponse().setBody("foo"));

    assertThat(testInterface.withBody("foo", "bar")).isEqualTo("foo");
    final RecordedRequest request1 = server.takeRequest();
    assertThat(request1.getPath()).isEqualTo("/withBody?foo=foo");
    assertThat(request1.getBody().readString(StandardCharsets.UTF_8)).isEqualTo("bar");

    assertThat(testInterface.withoutBody("foo")).isEqualTo("foo");
    final RecordedRequest request2 = server.takeRequest();
    assertThat(request2.getPath()).isEqualTo("/withoutBody?foo=foo");
    assertThat(request2.getBody().readString(StandardCharsets.UTF_8)).isEqualTo("");
  }

  @Test
  void followRedirectIsRespected() throws InterruptedException {
    final JaxRsTestInterface testInterface = buildTestInterface();

    String redirectPath = "/redirected";
    server.enqueue(buildMockResponseWithHeaderLocation(redirectPath));
    server.enqueue(new MockResponse().setBody("redirect"));
    Options options = buildRequestOptions(true);

    assertThat(testInterface.withOptions(options)).isEqualTo("redirect");
    assertThat(server.takeRequest().getPath()).isEqualTo("/withOptions");
    assertThat(server.takeRequest().getPath()).isEqualTo(redirectPath);
  }

  @Test
  void notFollowRedirectIsRespected() throws InterruptedException {
    final JaxRsTestInterface testInterface = buildTestInterface();

    String redirectPath = "/redirected";
    server.enqueue(buildMockResponseWithHeaderLocation(redirectPath));
    Options options = buildRequestOptions(false);

    FeignException feignException =
        assertThrows(FeignException.class, () -> testInterface.withOptions(options));
    assertThat(feignException.status()).isEqualTo(302);
    assertThat(server.takeRequest().getPath()).isEqualTo("/withOptions");
  }

  private JaxRsTestInterface buildTestInterface() {
    return Feign.builder()
        .contract(new JAXRSContract())
        .client(new ApacheHttpClient(HttpClientBuilder.create().build()))
        .target(JaxRsTestInterface.class, "http://localhost:" + server.getPort());
  }

  private static Options buildRequestOptions(boolean followRedirects) {
    return new Options(1, SECONDS,
        1, SECONDS, followRedirects);
  }

  private MockResponse buildMockResponseWithHeaderLocation(String redirectPath) {
    return new MockResponse().setResponseCode(302).addHeader("location",
        "http://localhost:" + server.getPort() + redirectPath);
  }

  @Path("/")
  public interface JaxRsTestInterface {
    @PUT
    @Path("/withBody")
    public String withBody(@QueryParam("foo") String foo, String bar);

    @PUT
    @Path("/withoutBody")
    public String withoutBody(@QueryParam("foo") String foo);

    @GET
    @Path("/withOptions")
    public String withOptions(Options options);
  }
}
