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
package feign.http2client;

import static org.assertj.core.api.Assertions.assertThat;

import feign.Request;
import feign.Request.HttpMethod;
import feign.Request.Options;
import feign.Response;
import feign.Util;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Preserves received response header values across both execution paths. */
@Timeout(30)
class Http2ClientHeaderValuesTest {

  private static final String BODY = "trace-body";
  private static final String PATH = "/trace";
  private static final long TIMEOUT_SECONDS = 5;

  /** Defaults, passed explicitly so the run uses a known timeout and redirect configuration. */
  private static final Options OPTIONS = new Options();

  private static Http2Client client() {
    return new Http2Client(
        HttpClient.newBuilder()
            .followRedirects(Redirect.ALWAYS)
            .version(Version.HTTP_2)
            .connectTimeout(Duration.ofMillis(OPTIONS.connectTimeoutMillis()))
            .build());
  }

  record HeaderCase(String scenario, String name, List<String> values) {

    @Override
    public String toString() {
      return scenario;
    }
  }

  static Stream<HeaderCase> headerCases() {
    return Stream.of(
        // three distinct values: an unordered collection hands them back in a different order
        new HeaderCase(
            "distinctValuesKeepReceivedOrder",
            "X-Trace-Step",
            List.of("step-1", "step-2", "step-3")),
        // the very same line twice: both occurrences must survive
        new HeaderCase(
            "repeatedValuesKeepEveryOccurrence",
            "Set-Cookie",
            List.of("session=abc; Path=/", "session=abc; Path=/")),
        // duplicates and distinct values mixed in a single header
        new HeaderCase(
            "mixedValuesKeepDuplicatesAndOrder",
            "Set-Cookie",
            List.of("session=old; Path=/", "session=new; Path=/api", "session=new; Path=/api")),
        // control: a single value is exposed as is
        new HeaderCase("singleValueIsExposed", "X-Trace-Step", List.of("step-only")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("headerCases")
  void syncExecuteExposesEveryValueInReceivedOrder(HeaderCase headerCase) throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(responseFor(headerCase));

      try (Response response = client().execute(request(server), OPTIONS)) {
        assertHeaderValues(response, headerCase);
      }

      assertRequestWasSent(server.takeRequest(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("headerCases")
  void asyncExecuteExposesEveryValueInReceivedOrder(HeaderCase headerCase) throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(responseFor(headerCase));

      final CompletableFuture<Response> future =
          client().execute(request(server), OPTIONS, Optional.empty());

      try (Response response = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        assertHeaderValues(response, headerCase);
      }

      assertRequestWasSent(server.takeRequest(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }
  }

  private static Request request(MockWebServer server) {
    return Request.create(
        HttpMethod.GET,
        "http://localhost:" + server.getPort() + PATH,
        Collections.emptyMap(),
        null,
        StandardCharsets.UTF_8,
        null);
  }

  /**
   * Writes one header line per value. Joining the values with commas would send a single value
   * instead of the repeated lines this test is about.
   */
  private static MockResponse responseFor(HeaderCase headerCase) {
    final MockResponse mockResponse = new MockResponse().setResponseCode(200).setBody(BODY);
    for (String value : headerCase.values()) {
      mockResponse.addHeader(headerCase.name(), value);
    }
    return mockResponse;
  }

  private static void assertHeaderValues(Response response, HeaderCase expected)
      throws IOException {
    assertThat(response.status()).isEqualTo(200);
    assertThat(Util.toString(response.body().asReader(StandardCharsets.UTF_8))).isEqualTo(BODY);

    // Response normalizes its headers through Util#caseInsensitiveCopyOf, which keeps duplicate
    // values and their received order while merging case variants of the header name.
    assertThat(response.headers().get(expected.name().toLowerCase(Locale.ROOT)))
        .as("%s values in received order", expected.name())
        .containsExactlyElementsOf(expected.values());
    assertThat(response.headers().get(expected.name().toUpperCase(Locale.ROOT)))
        .as("%s values found through a differently cased name", expected.name())
        .containsExactlyElementsOf(expected.values());
  }

  private static void assertRequestWasSent(RecordedRequest recordedRequest) {
    assertThat(recordedRequest).isNotNull();
    assertThat(recordedRequest.getMethod()).isEqualTo("GET");
    assertThat(recordedRequest.getPath()).isEqualTo(PATH);
  }
}
