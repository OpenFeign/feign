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
package feign.hc5;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import feign.Feign;
import feign.Feign.Builder;
import feign.FeignException;
import feign.Request;
import feign.client.AbstractClientTest;
import feign.jaxrs.JAXRSContract;
import java.nio.charset.StandardCharsets;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.junit.jupiter.api.Test;

/** Tests client-specific behavior, such as ensuring Content-Length is sent when specified. */
public class ApacheHttp5ClientTest extends AbstractClientTest {

  @Override
  public Builder newBuilder() {
    return Feign.builder().client(new ApacheHttp5Client());
  }

  @Test
  void queryParamsAreRespectedWhenBodyIsEmpty() throws Exception {
    final JaxRsTestInterface testInterface = buildTestInterface();

    server.enqueue(new MockResponse.Builder().body("foo").build());
    server.enqueue(new MockResponse.Builder().body("foo").build());

    assertThat(testInterface.withBody("foo", "bar")).isEqualTo("foo");
    final RecordedRequest request1 = server.takeRequest();
    assertThat(request1.getTarget()).isEqualTo("/withBody?foo=foo");
    assertThat(request1.getBody().string(StandardCharsets.UTF_8)).isEqualTo("bar");

    assertThat(testInterface.withoutBody("foo")).isEqualTo("foo");
    final RecordedRequest request2 = server.takeRequest();
    assertThat(request2.getTarget()).isEqualTo("/withoutBody?foo=foo");
    assertThat(request2.getBody().string(StandardCharsets.UTF_8)).isEmpty();
  }

  @Test
  void followRedirectsIsTrue() throws Exception {
    final JaxRsTestInterface testInterface = buildTestInterface();

    String redirectPath = getRedirectionUrl();
    server.enqueue(buildMockResponseWithLocationHeader(redirectPath).build());
    server.enqueue(new MockResponse.Builder().body("redirected").build());
    Request.Options options = buildRequestOptions(true);

    Object response = testInterface.withOptions(options);
    assertThat(response).isNotNull().isEqualTo("redirected");
    assertThat(server.takeRequest().getTarget()).isEqualTo("/withRequestOptions");
  }

  @Test
  void followRedirectsIsFalse() throws Exception {
    final JaxRsTestInterface testInterface = buildTestInterface();

    String redirectPath = getRedirectionUrl();
    server.enqueue(buildMockResponseWithLocationHeader(redirectPath).build());
    Request.Options options = buildRequestOptions(false);

    FeignException feignException =
        assertThatExceptionOfType(FeignException.class)
            .isThrownBy(() -> testInterface.withOptions(options))
            .actual();
    assertThat(feignException.status()).isEqualTo(302);
    assertThat(feignException.responseHeaders().get("location").stream().findFirst().orElse(null))
        .isEqualTo(redirectPath);
    assertThat(server.takeRequest().getTarget()).isEqualTo("/withRequestOptions");
  }

  private JaxRsTestInterface buildTestInterface() {
    return Feign.builder()
        .contract(new JAXRSContract())
        .client(new ApacheHttp5Client(HttpClientBuilder.create().build()))
        .target(JaxRsTestInterface.class, "http://localhost:" + server.getPort());
  }

  private MockResponse.Builder buildMockResponseWithLocationHeader(String redirectPath) {
    return new MockResponse.Builder().code(302).addHeader("location", redirectPath);
  }

  private String getRedirectionUrl() {
    return "http://localhost:" + server.getPort() + "/redirected";
  }

  private Request.Options buildRequestOptions(boolean followRedirects) {
    return new Request.Options(1, SECONDS, 1, SECONDS, followRedirects);
  }

  @Override
  @Test
  public void veryLongResponseNullLength() {
    assumeTrue(false, "HC5 client seems to hang with response size equalto Long.MAX");
  }

  @Override
  @Test
  public void contentTypeDefaultsToRequestCharset() throws Exception {
    assumeTrue(false, "this test is flaky on windows, but works fine.");
  }

  @Path("/")
  public interface JaxRsTestInterface {
    @PUT
    @Path("/withBody")
    String withBody(@QueryParam("foo") String foo, String bar);

    @PUT
    @Path("/withoutBody")
    String withoutBody(@QueryParam("foo") String foo);

    @GET
    @Path("/withRequestOptions")
    String withOptions(Request.Options options);
  }
}
