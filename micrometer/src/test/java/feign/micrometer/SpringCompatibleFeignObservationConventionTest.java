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
package feign.micrometer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import feign.Client;
import feign.Feign;
import feign.FeignException;
import feign.Param;
import feign.Request;
import feign.Request.HttpMethod;
import feign.RequestLine;
import feign.RequestTemplate;
import feign.Response;
import feign.Retryer;
import feign.Target.EmptyTarget;
import feign.Target.HardCodedTarget;
import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SpringCompatibleFeignObservationConvention}, verifying:
 *
 * <ul>
 *   <li>the opt-in convention emits exactly the low-cardinality key names used by Spring
 *       Framework's client observations ({@code RestTemplate}, {@code WebClient}), so that {@code
 *       http.client.requests} meters from Feign and Spring clients can be aggregated together by
 *       registries such as Prometheus (which require an identical key set for all meters of the
 *       same name);
 *   <li>values follow Spring's semantics (e.g. {@code uri} is the template path, {@code
 *       client.name} is the request host, missing response yields {@code status=CLIENT_ERROR} and
 *       {@code outcome=UNKNOWN});
 *   <li>the {@link DefaultFeignObservationConvention} (the default) keeps emitting the legacy Feign
 *       key names, so this change is non-breaking for existing dashboards.
 * </ul>
 *
 * <p>No real network is used: the underlying {@link Client} is stubbed to return a canned response.
 */
class SpringCompatibleFeignObservationConventionTest {

  private static final String CLIENT_NAME =
      FeignObservationDocumentation.SpringHttpClientTags.CLIENT_NAME.asString();
  private static final String EXCEPTION =
      FeignObservationDocumentation.SpringHttpClientTags.EXCEPTION.asString();
  private static final String METHOD =
      FeignObservationDocumentation.SpringHttpClientTags.METHOD.asString();
  private static final String OUTCOME =
      FeignObservationDocumentation.SpringHttpClientTags.OUTCOME.asString();
  private static final String STATUS =
      FeignObservationDocumentation.SpringHttpClientTags.STATUS.asString();
  private static final String URI_TAG =
      FeignObservationDocumentation.SpringHttpClientTags.URI.asString();

  interface TestClient {
    @RequestLine("GET /users/{id}")
    String get(@Param("id") long id);
  }

  interface AbsoluteUrlClient {
    @RequestLine("GET")
    String get(URI uri);
  }

  private TestObservationRegistry observationRegistry;

  @BeforeEach
  void setUp() {
    this.observationRegistry = TestObservationRegistry.create();
  }

  private static Client okClient() {
    return (request, options) ->
        Response.builder()
            .status(200)
            .reason("OK")
            .request(request)
            .headers(Collections.emptyMap())
            .build();
  }

  private static Client statusClient(int status) {
    return (request, options) ->
        Response.builder()
            .status(status)
            .reason("status " + status)
            .request(request)
            .headers(Collections.emptyMap())
            .build();
  }

  @Test
  void emitsExactlySpringClientKeyNamesAndValues() {
    TestClient feignClient =
        Feign.builder()
            .client(okClient())
            .addCapability(
                new MicrometerObservationCapability(
                    observationRegistry, SpringCompatibleFeignObservationConvention.INSTANCE))
            .target(new HardCodedTarget<>(TestClient.class, "http://localhost:8080"));

    feignClient.get(42);

    TestObservationRegistryAssert.assertThat(observationRegistry)
        .hasSingleObservationThat()
        .hasBeenStopped()
        .hasNameEqualTo("http.client.requests")
        // Prometheus requires all meters named http.client.requests to share one tag key set;
        // this is the exact set Spring Framework's DefaultClientRequestObservationConvention
        // emits for RestTemplate and WebClient (Spring Framework 6.1+ / 7.0).
        .hasOnlyKeys(CLIENT_NAME, EXCEPTION, METHOD, OUTCOME, STATUS, URI_TAG)
        .hasLowCardinalityKeyValue(METHOD, "GET")
        .hasLowCardinalityKeyValue(URI_TAG, "/users/{id}")
        .hasLowCardinalityKeyValue(STATUS, "200")
        .hasLowCardinalityKeyValue(OUTCOME, "SUCCESS")
        .hasLowCardinalityKeyValue(CLIENT_NAME, "localhost")
        .hasLowCardinalityKeyValue(EXCEPTION, KeyValue.NONE_VALUE);
  }

  @Test
  void keyValuesAreSortedByNameLikeSpringsConvention() {
    KeyValues keyValues =
        SpringCompatibleFeignObservationConvention.INSTANCE.getLowCardinalityKeyValues(
            contextFor(captureRequestTemplate(), 200));

    assertThat(keyValues.stream().map(KeyValue::getKey))
        .containsExactly(CLIENT_NAME, EXCEPTION, METHOD, OUTCOME, STATUS, URI_TAG);
  }

  @Test
  void outcomeFollowsHttpStatusSeriesLikeSpring() {
    assertThat(outcomeFor(100)).isEqualTo("INFORMATIONAL");
    assertThat(outcomeFor(204)).isEqualTo("SUCCESS");
    assertThat(outcomeFor(302)).isEqualTo("REDIRECTION");
    assertThat(outcomeFor(404)).isEqualTo("CLIENT_ERROR");
    assertThat(outcomeFor(503)).isEqualTo("SERVER_ERROR");
  }

  @Test
  void outcomeIsUnknownForStatusCodesSpringDoesNotDeclare() {
    // Spring's HttpOutcome only maps codes that are HttpStatus constants; any 2xx is SUCCESS.
    assertThat(outcomeFor(299)).isEqualTo("SUCCESS");
    assertThat(outcomeFor(306)).isEqualTo("UNKNOWN");
    // removed from HttpStatus in Spring Framework 7
    assertThat(outcomeFor(305)).isEqualTo("UNKNOWN");
    assertThat(outcomeFor(419)).isEqualTo("UNKNOWN");
    assertThat(outcomeFor(420)).isEqualTo("UNKNOWN");
    assertThat(outcomeFor(499)).isEqualTo("UNKNOWN");
    assertThat(outcomeFor(599)).isEqualTo("UNKNOWN");
    assertThat(outcomeFor(418)).isEqualTo("CLIENT_ERROR");
    assertThat(outcomeFor(511)).isEqualTo("SERVER_ERROR");
  }

  @Test
  void nullCarrierDoesNotThrow() {
    FeignContext context = new FeignContext(null);

    KeyValues keyValues =
        SpringCompatibleFeignObservationConvention.INSTANCE.getLowCardinalityKeyValues(context);

    assertThat(valueOf(keyValues, URI_TAG)).isEqualTo(KeyValue.NONE_VALUE);
    assertThat(valueOf(keyValues, METHOD)).isEqualTo(KeyValue.NONE_VALUE);
    assertThat(valueOf(keyValues, CLIENT_NAME)).isEqualTo(KeyValue.NONE_VALUE);
    assertThat(valueOf(keyValues, STATUS)).isEqualTo("CLIENT_ERROR");
    assertThat(valueOf(keyValues, OUTCOME)).isEqualTo("UNKNOWN");
  }

  @Test
  void errorStatusIsNotRecordedAsException() {
    // FeignException is raised by the error decoder after the client observation has stopped.
    TestClient feignClient =
        Feign.builder()
            .client(statusClient(404))
            .addCapability(
                new MicrometerObservationCapability(
                    observationRegistry, SpringCompatibleFeignObservationConvention.INSTANCE))
            .target(new HardCodedTarget<>(TestClient.class, "http://localhost:8080"));

    assertThatThrownBy(() -> feignClient.get(42)).isInstanceOf(FeignException.NotFound.class);

    TestObservationRegistryAssert.assertThat(observationRegistry)
        .hasSingleObservationThat()
        .hasBeenStopped()
        .hasLowCardinalityKeyValue(STATUS, "404")
        .hasLowCardinalityKeyValue(OUTCOME, "CLIENT_ERROR")
        .hasLowCardinalityKeyValue(EXCEPTION, KeyValue.NONE_VALUE);
  }

  @Test
  void missingResponseYieldsSpringClientErrorStatusAndUnknownOutcome() {
    // A failed request (no response) - Spring reports status=CLIENT_ERROR, outcome=UNKNOWN
    RequestTemplate requestTemplate = captureRequestTemplate();
    Request request =
        Request.create(
            HttpMethod.GET,
            "http://localhost:8080/users/42",
            Collections.emptyMap(),
            Request.Body.empty(),
            requestTemplate);
    FeignContext context = new FeignContext(request);
    context.setError(new SocketTimeoutException("connect timed out"));

    KeyValues keyValues =
        SpringCompatibleFeignObservationConvention.INSTANCE.getLowCardinalityKeyValues(context);

    assertThat(keyValues.stream().map(KeyValue::getKey))
        .containsExactly(CLIENT_NAME, EXCEPTION, METHOD, OUTCOME, STATUS, URI_TAG);
    assertThat(valueOf(keyValues, STATUS)).isEqualTo("CLIENT_ERROR");
    assertThat(valueOf(keyValues, OUTCOME)).isEqualTo("UNKNOWN");
    assertThat(valueOf(keyValues, EXCEPTION)).isEqualTo("SocketTimeoutException");
  }

  @Test
  void exceptionThrowsByClientAreRecordedWithSpringKeyNames() {
    IOException underlying = new SocketTimeoutException("connect timed out");

    Client client =
        (request, options) -> {
          throw underlying;
        };

    TestClient feignClient =
        Feign.builder()
            .client(client)
            .retryer(Retryer.NEVER_RETRY)
            .addCapability(
                new MicrometerObservationCapability(
                    observationRegistry, SpringCompatibleFeignObservationConvention.INSTANCE))
            .target(new HardCodedTarget<>(TestClient.class, "http://localhost:8080"));

    assertThatThrownBy(() -> feignClient.get(42)).isInstanceOf(Exception.class);

    TestObservationRegistryAssert.assertThat(observationRegistry)
        .hasSingleObservationThat()
        .hasBeenStopped()
        .hasOnlyKeys(CLIENT_NAME, EXCEPTION, METHOD, OUTCOME, STATUS, URI_TAG)
        .hasLowCardinalityKeyValue(STATUS, "CLIENT_ERROR")
        .hasLowCardinalityKeyValue(OUTCOME, "UNKNOWN")
        .hasLowCardinalityKeyValue(EXCEPTION, "SocketTimeoutException");
  }

  @Test
  void absoluteUriRequestCarriesHostAndRootPath() {
    // A Feign method invoked with an absolute URI argument has no path in its template (feign
    // normalizes an absent RequestLine path to "/"); the host of the request url is reported as
    // client.name, like Spring does for its clients.
    AbsoluteUrlClient feignClient =
        Feign.builder()
            .client(okClient())
            .addCapability(
                new MicrometerObservationCapability(
                    observationRegistry, SpringCompatibleFeignObservationConvention.INSTANCE))
            .target(EmptyTarget.create(AbsoluteUrlClient.class));

    feignClient.get(URI.create("http://absolute.example.com:9000/resource"));

    TestObservationRegistryAssert.assertThat(observationRegistry)
        .hasSingleObservationThat()
        .hasBeenStopped()
        .hasOnlyKeys(CLIENT_NAME, EXCEPTION, METHOD, OUTCOME, STATUS, URI_TAG)
        .hasLowCardinalityKeyValue(URI_TAG, "/")
        .hasLowCardinalityKeyValue(CLIENT_NAME, "absolute.example.com");
  }

  @Test
  void uriIsNoneWhenRequestTemplateHasNoMethodMetadata() {
    // A RequestTemplate not routed through RequestTemplateFactoryResolver (e.g. a hand-built one)
    // has no methodMetadata(); the convention must not throw and reports uri=none.
    RequestTemplate requestTemplate = new RequestTemplate();
    FeignContext context = contextFor(requestTemplate, 200);

    KeyValues keyValues =
        SpringCompatibleFeignObservationConvention.INSTANCE.getLowCardinalityKeyValues(context);

    assertThat(valueOf(keyValues, URI_TAG)).isEqualTo(KeyValue.NONE_VALUE);
    assertThat(valueOf(keyValues, CLIENT_NAME)).isEqualTo("localhost");
  }

  @Test
  void clientNameIsNoneWhenRequestUrlHasNoHost() {
    Request request =
        Request.create(
            HttpMethod.GET,
            "/relative/path",
            Collections.emptyMap(),
            Request.Body.empty(),
            captureRequestTemplate());
    FeignContext context = new FeignContext(request);
    context.setResponse(okResponse(request, 200));

    KeyValues keyValues =
        SpringCompatibleFeignObservationConvention.INSTANCE.getLowCardinalityKeyValues(context);

    assertThat(valueOf(keyValues, CLIENT_NAME)).isEqualTo(KeyValue.NONE_VALUE);
    assertThat(valueOf(keyValues, STATUS)).isEqualTo("200");
  }

  @Test
  void defaultConventionStillEmitsLegacyFeignKeyNames() {
    // Non-breaking guarantee: the default convention output is unchanged and does not adopt the
    // Spring key names.
    TestClient feignClient =
        Feign.builder()
            .client(okClient())
            .addCapability(new MicrometerObservationCapability(observationRegistry))
            .target(new HardCodedTarget<>(TestClient.class, "http://localhost:8080"));

    feignClient.get(42);

    TestObservationRegistryAssert.assertThat(observationRegistry)
        .hasSingleObservationThat()
        .hasBeenStopped()
        .hasOnlyKeys("http.method", "http.url", "http.status_code", "clientName")
        .hasLowCardinalityKeyValue("http.status_code", "200")
        .doesNotHaveLowCardinalityKeyValueWithKey(STATUS)
        .doesNotHaveLowCardinalityKeyValueWithKey(OUTCOME)
        .doesNotHaveLowCardinalityKeyValueWithKey(CLIENT_NAME)
        .doesNotHaveLowCardinalityKeyValueWithKey(EXCEPTION);
  }

  private static String outcomeFor(int status) {
    Request request =
        Request.create(
            HttpMethod.GET,
            "http://localhost:8080/users/42",
            Collections.emptyMap(),
            Request.Body.empty(),
            captureRequestTemplate());
    FeignContext context = new FeignContext(request);
    context.setResponse(okResponse(request, status));
    return valueOf(
        SpringCompatibleFeignObservationConvention.INSTANCE.getLowCardinalityKeyValues(context),
        OUTCOME);
  }

  private static Response okResponse(Request request, int status) {
    return Response.builder()
        .status(status)
        .reason("status " + status)
        .request(request)
        .headers(Collections.emptyMap())
        .build();
  }

  private static String valueOf(KeyValues keyValues, String key) {
    return keyValues.stream()
        .filter(kv -> kv.getKey().equals(key))
        .findFirst()
        .map(KeyValue::getValue)
        .orElseThrow(() -> new AssertionError("no key " + key + " in " + keyValues));
  }

  /** Drives a real Feign call to capture a fully-formed {@link RequestTemplate}. */
  private static RequestTemplate captureRequestTemplate() {
    AtomicReference<RequestTemplate> ref = new AtomicReference<>();
    Client capturing =
        (request, options) -> {
          ref.set(request.requestTemplate());
          return Response.builder()
              .status(200)
              .reason("OK")
              .request(request)
              .headers(Collections.emptyMap())
              .build();
        };
    TestClient feignClient =
        Feign.builder()
            .client(capturing)
            .target(new HardCodedTarget<>(TestClient.class, "http://localhost:8080"));
    feignClient.get(42);
    return ref.get();
  }

  private static FeignContext contextFor(RequestTemplate requestTemplate, int status) {
    Request request =
        Request.create(
            HttpMethod.GET,
            "http://localhost:8080/users/42",
            Collections.emptyMap(),
            Request.Body.empty(),
            requestTemplate);
    FeignContext context = new FeignContext(request);
    context.setResponse(okResponse(request, status));
    return context;
  }
}
