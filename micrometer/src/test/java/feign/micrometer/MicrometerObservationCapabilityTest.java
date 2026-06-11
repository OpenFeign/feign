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

import feign.AsyncClient;
import feign.AsyncFeign;
import feign.Client;
import feign.Feign;
import feign.RequestLine;
import feign.Retryer;
import feign.Target.HardCodedTarget;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Behavioural tests for {@link MicrometerObservationCapability} focusing on the cases that the
 * existing capability did not cover:
 *
 * <ul>
 *   <li>The observation is current (via {@code Observation.Scope}) while the client executes, so
 *       downstream handlers — most notably tracing handlers that propagate trace/span ids into the
 *       MDC — see the Feign-scoped observation rather than the parent.
 *   <li>Exceptions thrown by the underlying client that are <em>not</em> {@code FeignException} —
 *       {@code IOException}, {@code SocketTimeoutException}, runtime exceptions thrown by a custom
 *       {@code ErrorDecoder}, and so on — still close the observation and record the error.
 * </ul>
 */
class MicrometerObservationCapabilityTest {

  interface TestClient {
    @RequestLine("GET /")
    String get();
  }

  interface AsyncTestClient {
    @RequestLine("GET /")
    CompletableFuture<String> get();
  }

  private TestObservationRegistry observationRegistry;

  @BeforeEach
  void setUp() {
    this.observationRegistry = TestObservationRegistry.create();
  }

  @Test
  void scopeIsOpenDuringClientExecution() {
    AtomicReference<Observation> observedDuringExecute = new AtomicReference<>();
    Client client =
        (request, options) -> {
          observedDuringExecute.set(observationRegistry.getCurrentObservation());
          return feign.Response.builder()
              .status(200)
              .reason("OK")
              .request(request)
              .headers(java.util.Collections.emptyMap())
              .build();
        };

    TestClient feignClient =
        Feign.builder()
            .client(client)
            .addCapability(new MicrometerObservationCapability(observationRegistry))
            .target(new HardCodedTarget<>(TestClient.class, "http://localhost"));

    feignClient.get();

    assertThat(observedDuringExecute.get())
        .as("observation must be active (scoped) while the underlying client executes")
        .isNotNull();
    assertThat(observationRegistry.getCurrentObservation())
        .as("observation must no longer be current after the call returns")
        .isNull();
  }

  @Test
  void recordsNonFeignExceptionThrownByClient() {
    // Feign's SynchronousMethodHandler wraps IOExceptions, so callers don't see the original
    // throwable. The observation captured by the capability sits below that wrapping, and is the
    // only place the underlying error is recorded against the trace.
    SocketTimeoutException underlying = new SocketTimeoutException("connect timed out");

    Client client =
        (request, options) -> {
          throw underlying;
        };

    TestClient feignClient =
        Feign.builder()
            .client(client)
            .retryer(Retryer.NEVER_RETRY)
            .addCapability(new MicrometerObservationCapability(observationRegistry))
            .target(new HardCodedTarget<>(TestClient.class, "http://localhost"));

    assertThatThrownBy(feignClient::get).isInstanceOf(Exception.class);

    TestObservationRegistryAssert.assertThat(observationRegistry)
        .hasSingleObservationThat()
        .hasBeenStopped()
        .hasError(underlying);
  }

  @Test
  void recordsRuntimeExceptionThrownByClient() {
    RuntimeException underlying = new RuntimeException("boom");

    Client client =
        (request, options) -> {
          throw underlying;
        };

    TestClient feignClient =
        Feign.builder()
            .client(client)
            .retryer(Retryer.NEVER_RETRY)
            .addCapability(new MicrometerObservationCapability(observationRegistry))
            .target(new HardCodedTarget<>(TestClient.class, "http://localhost"));

    assertThatThrownBy(feignClient::get).isInstanceOf(Exception.class);

    TestObservationRegistryAssert.assertThat(observationRegistry)
        .hasSingleObservationThat()
        .hasBeenStopped()
        .hasError(underlying);
  }

  @Test
  void asyncScopeIsOpenDuringClientExecution() {
    AtomicReference<Observation> observedDuringExecute = new AtomicReference<>();
    AsyncClient<Object> client =
        (request, options, context) -> {
          observedDuringExecute.set(observationRegistry.getCurrentObservation());
          return CompletableFuture.completedFuture(
              feign.Response.builder()
                  .status(200)
                  .reason("OK")
                  .request(request)
                  .headers(java.util.Collections.emptyMap())
                  .build());
        };

    AsyncTestClient feignClient =
        AsyncFeign.<Object>builder()
            .client(client)
            .addCapability(new MicrometerObservationCapability(observationRegistry))
            .target(new HardCodedTarget<>(AsyncTestClient.class, "http://localhost"));

    feignClient.get().join();

    assertThat(observedDuringExecute.get())
        .as("observation must be active while the async client kicks off the request")
        .isNotNull();
  }

  @Test
  void asyncRecordsNonFeignExceptionFromFailedFuture() {
    IOException underlying = new IOException("connection reset");

    AsyncClient<Object> client =
        (request, options, context) -> {
          CompletableFuture<feign.Response> failed = new CompletableFuture<>();
          failed.completeExceptionally(underlying);
          return failed;
        };

    AsyncTestClient feignClient =
        AsyncFeign.<Object>builder()
            .client(client)
            .retryer(Retryer.NEVER_RETRY)
            .addCapability(new MicrometerObservationCapability(observationRegistry))
            .target(new HardCodedTarget<>(AsyncTestClient.class, "http://localhost"));

    assertThatThrownBy(() -> feignClient.get().join()).isInstanceOf(CompletionException.class);

    TestObservationRegistryAssert.assertThat(observationRegistry)
        .hasSingleObservationThat()
        .hasBeenStopped()
        .hasError(underlying);
  }

  @Test
  void asyncRecordsSynchronousExceptionFromClient() {
    RuntimeException underlying = new RuntimeException("immediate failure");

    AsyncClient<Object> client =
        (request, options, context) -> {
          throw underlying;
        };

    AsyncTestClient feignClient =
        AsyncFeign.<Object>builder()
            .client(client)
            .retryer(Retryer.NEVER_RETRY)
            .addCapability(new MicrometerObservationCapability(observationRegistry))
            .target(new HardCodedTarget<>(AsyncTestClient.class, "http://localhost"));

    assertThatThrownBy(feignClient::get).isInstanceOf(RuntimeException.class);

    TestObservationRegistryAssert.assertThat(observationRegistry)
        .hasSingleObservationThat()
        .hasBeenStopped()
        .hasError(underlying);
  }

  @Test
  void parentObservationIsRestoredAfterCall() {
    Observation parent = Observation.start("parent", observationRegistry);
    try (Observation.Scope ignored = parent.openScope()) {

      Client client =
          (request, options) ->
              feign.Response.builder()
                  .status(200)
                  .reason("OK")
                  .request(request)
                  .headers(java.util.Collections.emptyMap())
                  .build();

      TestClient feignClient =
          Feign.builder()
              .client(client)
              .addCapability(new MicrometerObservationCapability(observationRegistry))
              .target(new HardCodedTarget<>(TestClient.class, "http://localhost"));

      feignClient.get();

      assertThat(observationRegistry.getCurrentObservation())
          .as("parent observation must still be current after the Feign call completes")
          .isSameAs(parent);
    } finally {
      parent.stop();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void scopeReceivesObservationCarryingFeignContext() {
    AtomicReference<Observation.Context> seenContext = new AtomicReference<>();

    observationRegistry
        .observationConfig()
        .observationHandler(
            new ObservationHandler<Observation.Context>() {
              @Override
              public void onScopeOpened(Observation.Context context) {
                seenContext.set(context);
              }

              @Override
              public boolean supportsContext(Observation.Context context) {
                return true;
              }
            });

    Client client =
        (request, options) ->
            feign.Response.builder()
                .status(200)
                .reason("OK")
                .request(request)
                .headers(java.util.Collections.emptyMap())
                .build();

    TestClient feignClient =
        Feign.builder()
            .client(client)
            .addCapability(new MicrometerObservationCapability(observationRegistry))
            .target(new HardCodedTarget<>(TestClient.class, "http://localhost"));

    feignClient.get();

    assertThat(seenContext.get())
        .as("scope must propagate the FeignContext to ObservationHandler#onScopeOpened")
        .isInstanceOf(FeignContext.class);
  }
}
