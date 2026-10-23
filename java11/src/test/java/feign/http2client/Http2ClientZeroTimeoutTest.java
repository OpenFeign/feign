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

import static feign.Util.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import feign.Request;
import feign.Request.HttpMethod;
import feign.Request.Options;
import feign.Response;
import feign.Util;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Tests the documented zero connect and read timeout contract through both execution paths. */
@Timeout(30)
class Http2ClientZeroTimeoutTest {

  private static final String PATH = "/zero-timeout";
  private static final String BODY = "zero timeout body";
  private static final int POSITIVE_TIMEOUT_MILLIS = 5000;

  private final MockWebServer server = new MockWebServer();

  /** The two public execution paths of {@link Http2Client}. */
  enum Mode {
    SYNC {
      @Override
      Response execute(Http2Client client, Request request, Options options) throws IOException {
        return client.execute(request, options);
      }
    },
    ASYNC {
      @Override
      Response execute(Http2Client client, Request request, Options options) throws Exception {
        // bounded, so a broken client fails the test instead of hanging it
        return client.execute(request, options, Optional.empty()).get(10, TimeUnit.SECONDS);
      }
    };

    abstract Response execute(Http2Client client, Request request, Options options)
        throws Exception;
  }

  static Stream<Arguments> zeroAndPositiveTimeouts() {
    return Stream.of(Mode.values())
        .flatMap(
            mode ->
                Stream.of(
                    Arguments.of(mode, "zero read timeout", POSITIVE_TIMEOUT_MILLIS, 0),
                    Arguments.of(mode, "zero connect timeout", 0, POSITIVE_TIMEOUT_MILLIS),
                    Arguments.of(mode, "zero connect and read timeout", 0, 0),
                    Arguments.of(
                        mode,
                        "positive connect and read timeout",
                        POSITIVE_TIMEOUT_MILLIS,
                        POSITIVE_TIMEOUT_MILLIS)));
  }

  @AfterEach
  void shutdown() throws IOException {
    server.close();
  }

  @ParameterizedTest(name = "{0} with {1}")
  @MethodSource("zeroAndPositiveTimeouts")
  void executeHonoursZeroTimeout(
      Mode mode, String description, int connectTimeoutMillis, int readTimeoutMillis)
      throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200).setBody(BODY));
    final Options options = requestOptions(connectTimeoutMillis, readTimeoutMillis);

    // building the client from these options must accept a zero connect timeout as well
    final Response response = mode.execute(new Http2Client(options), request(), options);

    assertResponse(response, description);
    assertServerReceivedRequest(description);
  }

  @Test
  void zeroConnectTimeoutMatchesNativeClientWithoutConnectTimeout() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200).setBody(BODY));
    // a JDK client built without connectTimeout() reports an empty timeout, which has to be
    // treated as "no connect timeout" instead of as a different client configuration
    final Http2Client client =
        new Http2Client(
            HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .version(HttpClient.Version.HTTP_2)
                .build());
    final Options options = requestOptions(0, POSITIVE_TIMEOUT_MILLIS);

    final Response response = client.execute(request(), options);

    assertResponse(response, "native client without connect timeout");
    assertServerReceivedRequest("native client without connect timeout");
  }

  @Test
  void negativeConnectTimeoutIsStillRejected() {
    // a negative timeout is not "no timeout", it is invalid and has always been rejected
    assertThrows(
        IllegalArgumentException.class,
        () -> new Http2Client(requestOptions(-1, POSITIVE_TIMEOUT_MILLIS)));
  }

  @Test
  void negativeReadTimeoutIsStillRejectedBeforeSendingRequest() throws IOException {
    final Http2Client client = new Http2Client();
    final Options options = requestOptions(POSITIVE_TIMEOUT_MILLIS, -1);

    assertThrows(IllegalArgumentException.class, () -> client.execute(request(), options));
    assertThat(server.getRequestCount()).as("no request should be sent").isZero();
  }

  private static Options requestOptions(int connectTimeoutMillis, int readTimeoutMillis) {
    return new Options(
        connectTimeoutMillis,
        TimeUnit.MILLISECONDS,
        readTimeoutMillis,
        TimeUnit.MILLISECONDS,
        true);
  }

  private Request request() {
    return Request.create(
        HttpMethod.GET,
        server.url(PATH).toString(),
        Collections.emptyMap(),
        Request.Body.empty(),
        null);
  }

  private void assertResponse(Response response, String description) throws IOException {
    try (Response ignored = response) {
      assertThat(response.status()).as("%s: status", description).isEqualTo(200);
      assertThat(Util.toString(response.body().asReader(UTF_8))).isEqualTo(BODY);
    }
  }

  private void assertServerReceivedRequest(String description) throws InterruptedException {
    final RecordedRequest recordedRequest = server.takeRequest(5, TimeUnit.SECONDS);
    assertThat(recordedRequest).as("%s: server should receive %s", description, PATH).isNotNull();
    assertThat(recordedRequest.getMethod()).isEqualTo("GET");
    assertThat(recordedRequest.getPath()).isEqualTo(PATH);
  }
}
