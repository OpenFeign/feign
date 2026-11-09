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
package feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.google.gson.Gson;
import feign.codec.Decoder;
import feign.codec.PredicatedDecoder;
import feign.optionals.OptionalDecoder;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link BaseBuilder#decodeErrorResponses()}. */
class DecodeErrorResponsesTest {

  private static final String ERROR_BODY =
      "{\"message\":\"nope\",\"httpStatusCode\":500,\"isFailed\":true,\"errorCode\":42}";

  public final MockWebServer server = new MockWebServer();

  interface TestInterface {
    @RequestLine("GET /")
    BaseResponse get();

    @RequestLine("GET /")
    TypedResponse<BaseResponse> getTyped();

    @RequestLine("GET /")
    Optional<BaseResponse> getOptional();

    @RequestLine("GET /")
    Unrelated getUnrelated();

    @RequestLine("GET /")
    String getString();

    @RequestLine("DELETE /")
    void delete();
  }

  interface AsyncTestInterface {
    @RequestLine("GET /")
    CompletableFuture<BaseResponse> get();
  }

  public static class BaseResponse {
    String message;
    int httpStatusCode;
    Boolean isFailed;
    int errorCode;
  }

  public static class Unrelated {
    String name;
  }

  private TestInterface api(Feign.Builder builder) {
    return builder
        .decoder(new JsonDecoder())
        .retryer(Retryer.NEVER_RETRY)
        .target(TestInterface.class, "http://localhost:" + server.getPort());
  }

  private Feign.Builder builder() {
    return Feign.builder().decodeErrorResponses();
  }

  private static MockResponse json(int code, String body) {
    return new MockResponse()
        .setResponseCode(code)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }

  @Test
  void decodesErrorBodyInsteadOfThrowing() {
    server.enqueue(json(500, ERROR_BODY));

    BaseResponse response = api(builder()).get();

    assertThat(response.message).isEqualTo("nope");
    assertThat(response.isFailed).isTrue();
    assertThat(response.errorCode).isEqualTo(42);
  }

  @AfterEach
  void afterEachTest() throws IOException {
    server.close();
  }

  @Test
  void notFoundStillThrows() {
    server.enqueue(json(404, ERROR_BODY));

    assertThatExceptionOfType(FeignException.NotFound.class).isThrownBy(() -> api(builder()).get());
  }

  @Test
  void voidMethodStillThrows() {
    server.enqueue(json(500, ERROR_BODY));

    TestInterface api =
        builder()
            .decoder((response, type) -> null)
            .retryer(Retryer.NEVER_RETRY)
            .target(TestInterface.class, "http://localhost:" + server.getPort());

    assertThatExceptionOfType(FeignException.InternalServerError.class).isThrownBy(api::delete);
  }

  @Test
  void defaultDecoderReturnsTheErrorBodyAsString() {
    server.enqueue(new MockResponse().setResponseCode(401).setBody("token expired"));

    TestInterface api =
        builder()
            .retryer(Retryer.NEVER_RETRY)
            .target(TestInterface.class, "http://localhost:" + server.getPort());

    assertThat(api.getString()).isEqualTo("token expired");
  }

  @Test
  void defaultDecoderStillThrowsOnABodilessUnauthorized() {
    server.enqueue(new MockResponse().setResponseCode(401));

    TestInterface api =
        builder()
            .retryer(Retryer.NEVER_RETRY)
            .target(TestInterface.class, "http://localhost:" + server.getPort());

    assertThatExceptionOfType(FeignException.Unauthorized.class).isThrownBy(api::getString);
  }

  @Test
  void defaultDecoderStillThrowsOnNotFound() {
    server.enqueue(new MockResponse().setResponseCode(404).setBody("missing"));

    TestInterface api =
        builder()
            .retryer(Retryer.NEVER_RETRY)
            .target(TestInterface.class, "http://localhost:" + server.getPort());

    assertThatExceptionOfType(FeignException.NotFound.class).isThrownBy(api::getString);
  }

  @Test
  void throwsWhenTheErrorBodyIsLargerThanTheBufferLimit() {
    server.enqueue(json(500, largeErrorBody()));

    assertThatExceptionOfType(FeignException.InternalServerError.class)
        .isThrownBy(() -> api(builder()).get());
  }

  @Test
  void throwsWhenAChunkedErrorBodyIsLargerThanTheBufferLimit() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(500)
            .addHeader("Content-Type", "application/json")
            .setChunkedBody(largeErrorBody(), 1024));

    assertThatExceptionOfType(FeignException.InternalServerError.class)
        .isThrownBy(() -> api(builder()).get());
  }

  @Test
  void decodesAChunkedErrorBodyWithinTheBufferLimit() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(500)
            .addHeader("Content-Type", "application/json")
            .setChunkedBody(ERROR_BODY, 16));

    assertThat(api(builder()).get().errorCode).isEqualTo(42);
  }

  @Test
  void decoderThatIsNotPredicatedSeesEveryErrorBody() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(502)
            .addHeader("Content-Type", "text/html")
            .setBody("<html>Bad Gateway</html>"));

    TestInterface api =
        builder()
            .decoder((response, type) -> Util.toString(response.body().asReader(Util.UTF_8)))
            .retryer(Retryer.NEVER_RETRY)
            .target(TestInterface.class, "http://localhost:" + server.getPort());

    assertThat(api.getString()).isEqualTo("<html>Bad Gateway</html>");
  }

  @Test
  void decodesErrorBodyWhenErrorDecoderReturnsNull() {
    server.enqueue(json(500, ERROR_BODY));

    assertThat(api(builder().errorDecoder((methodKey, response) -> null)).get().errorCode)
        .isEqualTo(42);
  }

  @Test
  void throwsTheDecodeFailureWhenErrorDecoderReturnsNull() {
    server.enqueue(json(500, "not json at all"));

    assertThatExceptionOfType(FeignException.class)
        .isThrownBy(() -> api(builder().errorDecoder((methodKey, response) -> null)).get())
        .satisfies(e -> assertThat(e.getSuppressed()).isEmpty());
  }

  @Test
  void asyncClientDecodesErrorBody() throws Exception {
    server.enqueue(json(500, ERROR_BODY));

    AsyncTestInterface api =
        AsyncFeign.builder()
            .decoder(new JsonDecoder())
            .decodeErrorResponses()
            .target(AsyncTestInterface.class, "http://localhost:" + server.getPort());

    assertThat(api.get().get().errorCode).isEqualTo(42);
  }

  @Test
  void asyncClientThrowsWithoutTheFlag() {
    server.enqueue(json(500, ERROR_BODY));

    AsyncTestInterface api =
        AsyncFeign.builder()
            .decoder(new JsonDecoder())
            .target(AsyncTestInterface.class, "http://localhost:" + server.getPort());

    assertThatExceptionOfType(ExecutionException.class)
        .isThrownBy(() -> api.get().get())
        .withCauseInstanceOf(FeignException.InternalServerError.class);
  }

  @Test
  void decoderSeesTheStatusTheServerSent() {
    // The status is never rewritten, so a decoder can pick its schema by status.
    server.enqueue(json(500, ERROR_BODY));

    StatusRecordingDecoder decoder = new StatusRecordingDecoder();
    builder()
        .decoder(decoder)
        .retryer(Retryer.NEVER_RETRY)
        .target(TestInterface.class, "http://localhost:" + server.getPort())
        .get();

    assertThat(decoder.seenStatus).isEqualTo(500);
  }

  @Test
  void throwsWhenFlagIsNotSet() {
    server.enqueue(json(500, ERROR_BODY));

    assertThatExceptionOfType(FeignException.class).isThrownBy(() -> api(Feign.builder()).get());
  }

  @Test
  void appliesToEveryMethodOnTheClient() {
    // Documented consequence of the flag being per-client: a method whose return type is not an
    // error-body shape decodes the error body anyway, because decoders ignore unknown properties.
    // Methods that should keep throwing belong on a separate client.
    server.enqueue(json(500, ERROR_BODY));

    Unrelated response = api(builder()).getUnrelated();

    assertThat(response).isNotNull();
    assertThat(response.name).isNull();
  }

  @Test
  void throwsWhenDecoderDoesNotAcceptTheResponse() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(502)
            .addHeader("Content-Type", "text/html")
            .setBody("<html>Bad Gateway</html>"));

    assertThatExceptionOfType(FeignException.class).isThrownBy(() -> api(builder()).get());
  }

  @Test
  void throwsWhenBodyDoesNotDecode() {
    server.enqueue(json(500, "not json at all"));

    assertThatExceptionOfType(FeignException.class)
        .isThrownBy(() -> api(builder()).get())
        .satisfies(e -> assertThat(e.status()).isEqualTo(500))
        // the decode failure is kept, so the cause is still diagnosable
        .satisfies(e -> assertThat(e.getSuppressed()).hasSize(1));
  }

  @Test
  void retryableFailuresStillRetry() {
    server.enqueue(json(503, ERROR_BODY).addHeader("Retry-After", "1"));
    server.enqueue(json(200, "{\"message\":\"ok\"}"));

    BaseResponse response =
        builder()
            .decoder(new JsonDecoder())
            .retryer(new DefaultRetryer(1, 1, 2))
            .target(TestInterface.class, "http://localhost:" + server.getPort())
            .get();

    assertThat(response.message).isEqualTo("ok");
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  void typedResponseReportsTheRealStatus() {
    server.enqueue(json(500, ERROR_BODY));

    TypedResponse<BaseResponse> response = api(builder()).getTyped();

    assertThat(response.status()).isEqualTo(500);
    assertThat(response.body().message).isEqualTo("nope");
  }

  @Test
  void optionalIsPresentForAnErrorBody() {
    server.enqueue(json(500, ERROR_BODY));

    TestInterface api =
        builder()
            .decoder(new OptionalDecoder(new JsonDecoder()))
            .retryer(Retryer.NEVER_RETRY)
            .target(TestInterface.class, "http://localhost:" + server.getPort());

    assertThat(api.getOptional()).isPresent().get().extracting("message").isEqualTo("nope");
  }

  @Test
  void threeHundredsAreLeftAlone() {
    // 304 rather than a redirect: the client follows a Location itself, before Feign sees it.
    server.enqueue(json(304, ERROR_BODY));

    assertThatExceptionOfType(FeignException.class).isThrownBy(() -> api(builder()).get());
  }

  private static String largeErrorBody() {
    return "{\"message\":\"" + "x".repeat(9000) + "\",\"errorCode\":42}";
  }

  /** Records the status the decoder was handed. */
  static class StatusRecordingDecoder extends JsonDecoder {
    int seenStatus;

    @Override
    public Object decode(Response response, Type type) throws IOException {
      seenStatus = response.status();
      return super.decode(response, type);
    }
  }

  /** A minimal JSON decoder that declares itself, so {@code canDecode} is exercised. */
  static class JsonDecoder implements Decoder, PredicatedDecoder {
    private final Gson gson = new Gson();

    @Override
    public boolean canDecode(Response response, Type type) {
      return Util.isJsonContentType(response);
    }

    @Override
    public Object decode(Response response, Type type) throws IOException {
      if (response.body() == null) {
        return null;
      }
      return gson.fromJson(response.body().asReader(Util.UTF_8), type);
    }
  }
}
