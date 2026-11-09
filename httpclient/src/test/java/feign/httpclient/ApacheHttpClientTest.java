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
import static org.junit.jupiter.api.Assertions.assertThrows;

import feign.Feign;
import feign.Feign.Builder;
import feign.FeignException;
import feign.Request;
import feign.Request.Options;
import feign.Response;
import feign.RetryableException;
import feign.Retryer;
import feign.Util;
import feign.client.AbstractClientTest;
import feign.jaxrs.JAXRSContract;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.ProtocolException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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

    server.enqueue(new MockResponse().setResponseCode(303));

    RetryableException exception =
        assertThrows(RetryableException.class, () -> api.withoutBody("foo"));

    assertThat(exception.getCause()).isInstanceOf(ClientProtocolException.class);
    assertThat(exception.getCause()).hasCauseInstanceOf(ProtocolException.class);
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

  /**
   * Apache HttpClient's internal retry handler treats every request without an entity as idempotent
   * and retries it when the connection is lost before the response arrives. A bodyless request of
   * an idempotent method is sent without an entity, so it is retried once. The requests here go to
   * the client directly, so the retry can only come from Apache and not from a Feign {@link
   * Retryer}.
   */
  @ParameterizedTest
  @EnumSource(
      value = Request.HttpMethod.class,
      names = {"GET", "HEAD", "OPTIONS", "TRACE", "DELETE"})
  void bodylessIdempotentRequestIsRetriedAfterLostConnection(Request.HttpMethod httpMethod)
      throws Exception {
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
    server.enqueue(successResponse(httpMethod));

    try (CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        Response response = execute(httpClient, request(httpMethod, null))) {
      assertThat(response.status()).isEqualTo(200);
      if (httpMethod != Request.HttpMethod.HEAD) {
        assertThat(Util.toString(response.body().asReader(StandardCharsets.UTF_8))).isEqualTo("ok");
      }
    }

    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  /**
   * A bodyless request of every other method keeps its empty entity, so Apache HttpClient does not
   * retry it after a lost connection.
   */
  @ParameterizedTest
  @EnumSource(
      value = Request.HttpMethod.class,
      names = {"POST", "PUT", "PATCH", "QUERY"})
  void bodylessRequestThatMayNotBeRetriedIsNotRetriedAfterLostConnection(
      Request.HttpMethod httpMethod) throws Exception {
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
    server.enqueue(successResponse(httpMethod));

    try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
      assertThrows(IOException.class, () -> execute(httpClient, request(httpMethod, null)));
    }

    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  /**
   * A request that carries a body keeps its entity, so Apache HttpClient does not retry it after a
   * lost connection. Only POST is exercised over the wire: MockWebServer does not read a request
   * body of a GET, so a bodyful GET hangs the exchange instead of reaching the server.
   */
  @Test
  void postWithBodyIsNotRetriedAfterLostConnection() throws Exception {
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
    server.enqueue(successResponse(Request.HttpMethod.POST));

    try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
      assertThrows(
          IOException.class, () -> execute(httpClient, request(Request.HttpMethod.POST, "bar")));
    }

    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  /**
   * A GET that carries a body is still converted into an entity-enclosing request that carries that
   * body, so Apache HttpClient keeps treating it as non-idempotent. The conversion is asserted
   * rather than the exchange, because MockWebServer does not read a request body of a GET.
   */
  @Test
  void getWithBodyIsConvertedToEntityEnclosingRequest() throws Exception {
    final Request request =
        Request.create(
            Request.HttpMethod.GET,
            "http://localhost:" + server.getPort() + "/withoutBody?foo=foo",
            Collections.emptyMap(),
            "bar".getBytes(StandardCharsets.UTF_8),
            null,
            null);

    try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
      final HttpUriRequest httpUriRequest =
          new ApacheHttpClient(httpClient)
              .toHttpUriRequest(request, new Options(2, SECONDS, 2, SECONDS, true));

      assertThat(httpUriRequest).isInstanceOf(HttpEntityEnclosingRequest.class);
      assertThat(httpUriRequest.getMethod()).isEqualTo("GET");
      assertThat(httpUriRequest.getURI().getRawQuery()).isEqualTo("foo=foo");

      final HttpEntity entity = ((HttpEntityEnclosingRequest) httpUriRequest).getEntity();
      assertThat(entity).isNotNull();
      assertThat(EntityUtils.toString(entity, StandardCharsets.UTF_8)).isEqualTo("bar");
    }
  }

  /**
   * Query parameters of a bodyless GET keep travelling on the request line, now that the request
   * carries no entity.
   */
  @Test
  void bodylessGetKeepsQueryParamsOnRequestLine() throws Exception {
    server.enqueue(new MockResponse().setBody("ok"));

    final JaxRsTestInterface testInterface = buildTestInterface();

    assertThat(testInterface.getWithoutBody("foo")).isEqualTo("ok");
    final RecordedRequest request = server.takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).isEqualTo("/withoutBody?foo=foo");
  }

  /**
   * Bodyless POST and PUT keep the empty body and the query parameters on the request line, so
   * their request shape is the same as before.
   */
  @Test
  void bodylessPostAndPutKeepQueryParamsOnRequestLine() throws Exception {
    server.enqueue(new MockResponse().setBody("ok"));
    server.enqueue(new MockResponse().setBody("ok"));

    final JaxRsTestInterface testInterface = buildTestInterface();

    assertThat(testInterface.postWithoutBody("foo")).isEqualTo("ok");
    final RecordedRequest postRequest = server.takeRequest();
    assertThat(postRequest.getMethod()).isEqualTo("POST");
    assertThat(postRequest.getPath()).isEqualTo("/withoutBody?foo=foo");
    assertThat(postRequest.getBody().readString(StandardCharsets.UTF_8)).isEqualTo("");

    assertThat(testInterface.withoutBody("foo")).isEqualTo("ok");
    final RecordedRequest putRequest = server.takeRequest();
    assertThat(putRequest.getMethod()).isEqualTo("PUT");
    assertThat(putRequest.getPath()).isEqualTo("/withoutBody?foo=foo");
    assertThat(putRequest.getBody().readString(StandardCharsets.UTF_8)).isEqualTo("");
  }

  private Response execute(CloseableHttpClient httpClient, Request request) throws IOException {
    return new ApacheHttpClient(httpClient)
        .execute(request, new Options(2, SECONDS, 2, SECONDS, true));
  }

  private Request request(Request.HttpMethod httpMethod, String body) {
    return Request.create(
        httpMethod,
        "http://localhost:" + server.getPort() + "/withoutBody",
        Collections.emptyMap(),
        body == null ? null : body.getBytes(StandardCharsets.UTF_8),
        null,
        null);
  }

  /** A HEAD response has no body for the client, so only the status is left to assert. */
  private static MockResponse successResponse(Request.HttpMethod httpMethod) {
    return httpMethod == Request.HttpMethod.HEAD
        ? new MockResponse().setResponseCode(200)
        : new MockResponse().setBody("ok");
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

  private MockResponse buildMockResponseWithHeaderLocation(String redirectPath) {
    return new MockResponse()
        .setResponseCode(302)
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

    @GET
    @Path("/withoutBody")
    public String getWithoutBody(@QueryParam("foo") String foo);

    @POST
    @Path("/withoutBody")
    public String postWithoutBody(@QueryParam("foo") String foo);
  }
}
