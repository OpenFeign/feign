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
package feign.micrometer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import feign.AsyncFeign;
import feign.Capability;
import feign.Feign;
import feign.FeignException;
import feign.Param;
import feign.RequestLine;
import feign.mock.HttpMethod;
import feign.mock.MockClient;
import feign.mock.MockTarget;

public abstract class AbstractMetricsTestBase<MR, METRIC_ID, METRIC> {

  public interface SimpleSource {

    @RequestLine("GET /get")
    String get(String body);
  }


  public interface CompletableSource {

    @RequestLine("GET /get")
    CompletableFuture<String> get(String body);
  }


  protected MR metricsRegistry;

  @BeforeEach
  public final void initializeMetricRegistry() {
    this.metricsRegistry = createMetricsRegistry();
  }

  protected abstract MR createMetricsRegistry();

  @Test
  final void addMetricsCapability() {
    final SimpleSource source =
        customizeBuilder(Feign.builder()
            .client(new MockClient().ok(HttpMethod.GET, "/get", "1234567890abcde"))
            .addCapability(createMetricCapability()))
                .target(new MockTarget<>(SimpleSource.class));

    source.get("0x3456789");

    assertMetricsCapability(false);
  }

  @Test
  final void addAsyncMetricsCapability() {
    final CompletableSource source =
        customizeBuilder(AsyncFeign.builder()
            .client(new MockClient().ok(HttpMethod.GET, "/get", "1234567890abcde"))
            .addCapability(createMetricCapability()))
                .target(new MockTarget<>(CompletableSource.class));

    source.get("0x3456789").join();

    assertMetricsCapability(true);
  }

  private void assertMetricsCapability(boolean asyncClient) {
    Map<METRIC_ID, METRIC> metrics = getFeignMetrics();
    assertThat(metrics).hasSize(7);
    metrics
        .keySet()
        .forEach(
            metricId -> assertThat(doesMetricIdIncludeClient(metricId))
                .as("Expect all metric names to include client name:" + metricId).isTrue());
    metrics
        .keySet()
        .forEach(
            metricId -> assertThat(doesMetricIncludeVerb(metricId, "get"))
                .as("Expect all metric names to include method name:" + metricId).isTrue());
    metrics
        .keySet()
        .forEach(
            metricId -> assertThat(doesMetricIncludeHost(metricId))
                .as("Expect all metric names to include host name:" + metricId).isTrue());

    final Map<METRIC_ID, METRIC> clientMetrics;
    if (asyncClient) {
      clientMetrics =
          getFeignMetrics().entrySet().stream()
              .filter(entry -> isAsyncClientMetric(entry.getKey()))
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    } else {
      clientMetrics =
          getFeignMetrics().entrySet().stream()
              .filter(entry -> isClientMetric(entry.getKey()))
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    assertThat(clientMetrics).hasSize(2);
    clientMetrics.values().stream()
        .filter(this::doesMetricHasCounter)
        .forEach(metric -> assertEquals(1, getMetricCounter(metric)));

    final Map<METRIC_ID, METRIC> decoderMetrics =
        getFeignMetrics().entrySet().stream()
            .filter(entry -> isDecoderMetric(entry.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    assertThat(decoderMetrics).hasSize(2);
    decoderMetrics.values().stream()
        .filter(this::doesMetricHasCounter)
        .forEach(metric -> assertEquals(1, getMetricCounter(metric)));
  }

  protected abstract boolean doesMetricIncludeHost(METRIC_ID metricId);

  protected abstract boolean doesMetricIncludeVerb(METRIC_ID metricId, String verb);

  protected abstract boolean doesMetricIdIncludeClient(METRIC_ID metricId);

  protected abstract Capability createMetricCapability();

  protected abstract Map<METRIC_ID, METRIC> getFeignMetrics();

  @Test
  void clientPropagatesUncheckedException() {
    final AtomicReference<FeignException.NotFound> notFound = new AtomicReference<>();

    final SimpleSource source =
        customizeBuilder(Feign.builder()
            .client(
                (request, options) -> {
                  notFound.set(new FeignException.NotFound("test", request, null, null));
                  throw notFound.get();
                })
            .addCapability(createMetricCapability()))
                .target(new MockTarget<>(MicrometerCapabilityTest.SimpleSource.class));

    try {
      source.get("0x3456789");
      fail("Should throw NotFound exception");
    } catch (FeignException.NotFound e) {
      assertThat(e).isSameAs(notFound.get());
    }

    assertThat(getMetric("http_response_code", "http_status", "404", "status_group", "4xx",
        "http_method", "GET")).isNotNull();
  }

  protected abstract METRIC getMetric(String suffix, String... tags);

  @Test
  void decoderPropagatesUncheckedException() {
    final AtomicReference<FeignException.NotFound> notFound = new AtomicReference<>();

    final SimpleSource source =
        customizeBuilder(Feign.builder()
            .client(new MockClient().ok(HttpMethod.GET, "/get", "1234567890abcde"))
            .decoder(
                (response, type) -> {
                  notFound.set(new FeignException.NotFound("test", response.request(), null, null));
                  throw notFound.get();
                })
            .addCapability(createMetricCapability()))
                .target(new MockTarget<>(MicrometerCapabilityTest.SimpleSource.class));

    FeignException.NotFound thrown =
        assertThrows(FeignException.NotFound.class, () -> source.get("0x3456789"));
    assertThat(thrown).isSameAs(notFound.get());
  }

  @Test
  void shouldMetricCollectionWithCustomException() {
    final SimpleSource source =
        customizeBuilder(Feign.builder()
            .client(
                (request, options) -> {
                  throw new RuntimeException("Test error");
                })
            .addCapability(createMetricCapability()))
                .target(new MockTarget<>(MicrometerCapabilityTest.SimpleSource.class));

    RuntimeException thrown = assertThrows(RuntimeException.class, () -> source.get("0x3456789"));
    assertThat(thrown.getMessage()).isEqualTo("Test error");

    assertThat(getMetric("exception", "exception_name", "RuntimeException", "method", "get",
        "root_cause_name", "RuntimeException")).isNotNull();
  }

  @Test
  void clientMetricsHaveUriLabel() {
    final SimpleSource source =
        customizeBuilder(Feign.builder()
            .client(new MockClient().ok(HttpMethod.GET, "/get", "1234567890abcde"))
            .addCapability(createMetricCapability()))
                .target(new MockTarget<>(SimpleSource.class));

    source.get("0x3456789");

    final Map<METRIC_ID, METRIC> clientMetrics =
        getFeignMetrics().entrySet().stream()
            .filter(entry -> isClientMetric(entry.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    clientMetrics
        .keySet()
        .forEach(
            metricId -> assertThat(doesMetricIncludeUri(metricId, "/get"))
                .as("Expect all Client metric names to include uri:" + metricId).isTrue());
  }

  public interface SourceWithPathExpressions {

    @RequestLine("GET /get/{id}")
    String get(@Param("id") String id, String body);
  }

  @Test
  void clientMetricsHaveUriLabelWithPathExpression() {
    final SourceWithPathExpressions source =
        customizeBuilder(Feign.builder()
            .client(new MockClient().ok(HttpMethod.GET, "/get/123", "1234567890abcde"))
            .addCapability(createMetricCapability()))
                .target(new MockTarget<>(SourceWithPathExpressions.class));

    source.get("123", "0x3456789");

    final Map<METRIC_ID, METRIC> clientMetrics =
        getFeignMetrics().entrySet().stream()
            .filter(entry -> isClientMetric(entry.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    clientMetrics
        .keySet()
        .forEach(
            metricId -> assertThat(doesMetricIncludeUri(metricId, "/get/{id}"))
                .as("Expect all Client metric names to include uri as aggregated path expression:"
                    + metricId)
                .isTrue());
  }

  @Test
  void decoderExceptionCounterHasUriLabelWithPathExpression() {
    final AtomicReference<FeignException.NotFound> notFound = new AtomicReference<>();

    final SourceWithPathExpressions source =
        customizeBuilder(Feign.builder()
            .client(new MockClient().ok(HttpMethod.GET, "/get/123", "1234567890abcde"))
            .decoder(
                (response, type) -> {
                  notFound.set(new FeignException.NotFound("test", response.request(), null, null));
                  throw notFound.get();
                })
            .addCapability(createMetricCapability()))
                .target(new MockTarget<>(MicrometerCapabilityTest.SourceWithPathExpressions.class));

    FeignException.NotFound thrown =
        assertThrows(FeignException.NotFound.class, () -> source.get("123", "0x3456789"));
    assertThat(thrown).isSameAs(notFound.get());

    final Map<METRIC_ID, METRIC> decoderMetrics =
        getFeignMetrics().entrySet().stream()
            .filter(entry -> isDecoderMetric(entry.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    decoderMetrics
        .keySet()
        .forEach(
            metricId -> assertThat(doesMetricIncludeUri(metricId, "/get/{id}"))
                .as("Expect all Decoder metric names to include uri as aggregated path expression:"
                    + metricId)
                .isTrue());
  }

  protected abstract boolean isClientMetric(METRIC_ID metricId);

  protected abstract boolean isAsyncClientMetric(METRIC_ID metricId);

  protected abstract boolean isDecoderMetric(METRIC_ID metricId);

  protected abstract boolean doesMetricIncludeUri(METRIC_ID metricId, String uri);

  protected abstract boolean doesMetricHasCounter(METRIC metric);

  protected abstract long getMetricCounter(METRIC metric);

  protected Feign.Builder customizeBuilder(Feign.Builder builder) {
    return builder;
  }

  protected <C> AsyncFeign.AsyncBuilder<C> customizeBuilder(AsyncFeign.AsyncBuilder<C> builder) {
    return builder;
  }
}
