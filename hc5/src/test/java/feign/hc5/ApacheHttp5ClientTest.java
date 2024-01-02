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
package feign.hc5;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.nio.charset.StandardCharsets;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.junit.jupiter.api.Test;
import feign.Feign;
import feign.Feign.Builder;
import feign.FeignException;
import feign.Request;
import feign.client.AbstractClientTest;
import feign.jaxrs.JAXRSContract;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;

/**
 * Tests client-specific behavior, such as ensuring Content-Length is sent when specified.
 */
public class ApacheHttp5ClientTest extends AbstractClientTest {

  @Override
  public Builder newBuilder() {
    return Feign.builder().client(new ApacheHttp5Client());
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
  void followRedirectsIsTrue() throws InterruptedException {
    final JaxRsTestInterface testInterface = buildTestInterface();

    String redirectPath = getRedirectionUrl();
    server.enqueue(buildMockResponseWithLocationHeader(redirectPath));
    server.enqueue(new MockResponse().setBody("redirected"));
    Request.Options options = buildRequestOptions(true);

    Object response = testInterface.withOptions(options);
    assertThat(response).isNotNull();
    assertThat(response).isEqualTo("redirected");
    assertThat(server.takeRequest().getPath()).isEqualTo("/withRequestOptions");
  }

  @Test
  void followRedirectsIsFalse() throws InterruptedException {
    final JaxRsTestInterface testInterface = buildTestInterface();

    String redirectPath = getRedirectionUrl();
    server.enqueue(buildMockResponseWithLocationHeader(redirectPath));
    Request.Options options = buildRequestOptions(false);

    FeignException feignException =
        assertThrows(FeignException.class, () -> testInterface.withOptions(options));
    assertThat(feignException.status()).isEqualTo(302);
    assertThat(feignException.responseHeaders().get("location").stream().findFirst().orElse(null))
        .isEqualTo(redirectPath);
    assertThat(server.takeRequest().getPath()).isEqualTo("/withRequestOptions");
  }

  private JaxRsTestInterface buildTestInterface() {
    return Feign.builder()
        .contract(new JAXRSContract())
        .client(new ApacheHttp5Client(HttpClientBuilder.create().build()))
        .target(JaxRsTestInterface.class, "http://localhost:" + server.getPort());
  }

  private MockResponse buildMockResponseWithLocationHeader(String redirectPath) {
    return new MockResponse().setResponseCode(302).addHeader("location", redirectPath);
  }

  private String getRedirectionUrl() {
    return "http://localhost:" + server.getPort() + "/redirected";
  }

  private Request.Options buildRequestOptions(boolean followRedirects) {
    return new Request.Options(1, SECONDS, 1, SECONDS, followRedirects);
  }

  @Override
  public void veryLongResponseNullLength() {
    assumeTrue(false, "HC5 client seems to hang with response size equalto Long.MAX");
  }

  @Override
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
