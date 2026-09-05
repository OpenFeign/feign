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
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
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

  @Test
  void decodesErrorBodyOn404() {
    // Regression guard: decoders commonly short-circuit 404 to an empty value without reading the
    // body, which would discard the envelope.
    server.enqueue(json(404, ERROR_BODY));

    assertThat(api(builder()).get().message).isEqualTo("nope");
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
            .decoder(new feign.optionals.OptionalDecoder(new JsonDecoder()))
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
