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
package feign.httpclient;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import feign.Feign;
import feign.Feign.Builder;
import feign.FeignException;
import feign.Request.Options;
import feign.RetryableException;
import feign.Retryer;
import feign.client.AbstractClientTest;
import feign.jaxrs.JAXRSContract;
import java.nio.charset.StandardCharsets;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import org.apache.http.ProtocolException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.jupiter.api.Test;

/** Tests client-specific behavior, such as ensuring Content-Length is sent when specified. */
public class ApacheHttpClientTest extends AbstractClientTest {

  @Override
  public Builder newBuilder() {
    return Feign.builder().client(new ApacheHttpClient());
  }

  @Test
  void redirectWithoutLocationHeaderKeepsRetryableExceptionWhenPropagationPolicyIsUnwrap() {
    JaxRsTestInterface api =
        Feign.builder()
            .contract(new JAXRSContract())
            .exceptionPropagationPolicy(feign.ExceptionPropagationPolicy.UNWRAP)
            .retryer(Retryer.NEVER_RETRY)
            .client(new ApacheHttpClient(HttpClientBuilder.create().build()))
            .target(JaxRsTestInterface.class, "http://localhost:" + server.getPort());

    server.enqueue(new MockResponse.Builder().code(303).build());

    RetryableException exception =
        assertThatExceptionOfType(RetryableException.class)
            .isThrownBy(() -> api.withoutBody("foo"))
            .actual();

    assertThat(exception.getCause()).isInstanceOf(ClientProtocolException.class);
    assertThat(exception.getCause()).hasCauseInstanceOf(ProtocolException.class);
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
  void followRedirectIsRespected() throws Exception {
    final JaxRsTestInterface testInterface = buildTestInterface();

    String redirectPath = "/redirected";
    server.enqueue(buildMockResponseWithHeaderLocation(redirectPath).build());
    server.enqueue(new MockResponse.Builder().body("redirect").build());
    Options options = buildRequestOptions(true);

    assertThat(testInterface.withOptions(options)).isEqualTo("redirect");
    assertThat(server.takeRequest().getTarget()).isEqualTo("/withOptions");
    assertThat(server.takeRequest().getTarget()).isEqualTo(redirectPath);
  }

  @Test
  void notFollowRedirectIsRespected() throws Exception {
    final JaxRsTestInterface testInterface = buildTestInterface();

    String redirectPath = "/redirected";
    server.enqueue(buildMockResponseWithHeaderLocation(redirectPath).build());
    Options options = buildRequestOptions(false);

    FeignException feignException =
        assertThatExceptionOfType(FeignException.class)
            .isThrownBy(() -> testInterface.withOptions(options))
            .actual();
    assertThat(feignException.status()).isEqualTo(302);
    assertThat(server.takeRequest().getTarget()).isEqualTo("/withOptions");
  }

  private JaxRsTestInterface buildTestInterface() {
    return Feign.builder()
        .contract(new JAXRSContract())
        .client(new ApacheHttpClient(HttpClientBuilder.create().build()))
        .target(JaxRsTestInterface.class, "http://localhost:" + server.getPort());
  }

  private static Options buildRequestOptions(boolean followRedirects) {
    return new Options(1, SECONDS, 1, SECONDS, followRedirects);
  }

  private MockResponse.Builder buildMockResponseWithHeaderLocation(String redirectPath) {
    return new MockResponse.Builder()
        .code(302)
        .addHeader("location", "http://localhost:" + server.getPort() + redirectPath);
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
