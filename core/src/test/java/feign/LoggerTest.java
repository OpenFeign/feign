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

import static feign.Util.enumForName;
import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import feign.Logger.Level;
import feign.Request.ProtocolVersion;
import feign.core.DefaultClient;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class LoggerTest {
  public final MockWebServer server = new MockWebServer();
  public final RecordingLogger logger = new RecordingLogger();

  @BeforeEach
  void setUp() throws IOException {
    server.start();
  }

  @AfterEach
  void tearDown() throws IOException {
    server.close();
  }

  interface SendsStuff {

    @RequestLine("POST /")
    @Headers({"Content-Type: application/json", "X-Token: qwerty"})
    @Body(
        "%7B\"customer_name\": \"{customer_name}\", \"user_name\": \"{user_name}\", \"password\":"
            + " \"{password}\"%7D")
    String login(
        @Param("customer_name") String customer,
        @Param("user_name") String user,
        @Param("password") String password);
  }

  @Nested
  public class LogLevelEmitsTest {

    private Level logLevel;

    public void initLogLevelEmitsTest(Level logLevel, List<String> expectedMessages) {
      this.logLevel = logLevel;
      logger.expectMessages(expectedMessages);
    }

    public static Iterable<Object[]> data() {
      return Arrays.asList(
          new Object[][] {
            {Level.NONE, Collections.emptyList()},
            {
              Level.BASIC,
              Arrays.asList(
                  "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
                  "\\[SendsStuff#login\\] <--- HTTP/1.1 200 OK \\([0-9]+ms\\)")
            },
            {
              Level.HEADERS,
              Arrays.asList(
                  "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
                  "\\[SendsStuff#login\\] Content-Length: 80",
                  "\\[SendsStuff#login\\] Content-Type: application/json",
                  "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
                  "\\[SendsStuff#login\\] <--- HTTP/1.1 200 OK \\([0-9]+ms\\)",
                  "\\[SendsStuff#login\\] content-length: 3",
                  "\\[SendsStuff#login\\] <--- END HTTP \\(3-byte body\\)")
            },
            {
              Level.FULL,
              Arrays.asList(
                  "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
                  "\\[SendsStuff#login\\] Content-Length: 80",
                  "\\[SendsStuff#login\\] Content-Type: application/json",
                  "\\[SendsStuff#login\\] ",
                  "\\[SendsStuff#login\\] \\{\"customer_name\": \"netflix\", \"user_name\":"
                      + " \"denominator\", \"password\": \"password\"\\}",
                  "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
                  "\\[SendsStuff#login\\] <--- HTTP/1.1 200 OK \\([0-9]+ms\\)",
                  "\\[SendsStuff#login\\] content-length: 3",
                  "\\[SendsStuff#login\\] ",
                  "\\[SendsStuff#login\\] foo",
                  "\\[SendsStuff#login\\] <--- END HTTP \\(3-byte body\\)")
            }
          });
    }

    @MethodSource("data")
    @ParameterizedTest
    void levelEmits(Level logLevel, List<String> expectedMessages) {
      initLogLevelEmitsTest(logLevel, expectedMessages);
      server.enqueue(
          new MockResponse.Builder().setHeader("Y-Powered-By", "Mock").body("foo").build());

      SendsStuff api =
          Feign.builder()
              .logger(logger)
              .logLevel(logLevel)
              .target(SendsStuff.class, "http://localhost:" + server.getPort());

      api.login("netflix", "denominator", "password");
    }
  }

  @Nested
  public class ReasonPhraseOptionalTest {

    private Level logLevel;

    public void initReasonPhraseOptional(Level logLevel, List<String> expectedMessages) {
      this.logLevel = logLevel;
      logger.expectMessages(expectedMessages);
    }

    public static Iterable<Object[]> data() {
      return Arrays.asList(
          new Object[][] {
            {
              Level.BASIC,
              Arrays.asList(
                  "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
                  "\\[SendsStuff#login\\] <--- HTTP/1.1 200 \\([0-9]+ms\\)")
            },
          });
    }

    @MethodSource("data")
    @ParameterizedTest
    void reasonPhraseOptional(Level logLevel, List<String> expectedMessages) {
      initReasonPhraseOptional(logLevel, expectedMessages);
      server.enqueue(new MockResponse.Builder().status("HTTP/1.1 " + 200).build());

      SendsStuff api =
          Feign.builder()
              .logger(logger)
              .logLevel(logLevel)
              .target(SendsStuff.class, "http://localhost:" + server.getPort());

      api.login("netflix", "denominator", "password");
    }
  }

  @Nested
  public class HttpProtocolVersionTest {

    private Level logLevel;
    private String protocolVersionName;

    public void initHttpProtocolVersionTest(
        Level logLevel, String protocolVersionName, List<String> expectedMessages) {
      this.logLevel = logLevel;
      this.protocolVersionName = protocolVersionName;
      logger.expectMessages(expectedMessages);
    }

    public static Iterable<Object[]> data() {
      return Arrays.asList(
          new Object[][] {
            {
              Level.BASIC,
              null,
              Arrays.asList(
                  "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
                  "\\[SendsStuff#login\\] <--- HTTP/1.1 200 \\([0-9]+ms\\)")
            },
            {
              Level.BASIC,
              "HTTP/1.1",
              Arrays.asList(
                  "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
                  "\\[SendsStuff#login\\] <--- HTTP/1.1 200 \\([0-9]+ms\\)")
            },
            {
              Level.BASIC,
              "HTTP/2.0",
              Arrays.asList(
                  "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
                  "\\[SendsStuff#login\\] <--- HTTP/2.0 200 \\([0-9]+ms\\)")
            },
            {
              Level.BASIC,
              "HTTP-XYZ",
              Arrays.asList(
                  "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
                  "\\[SendsStuff#login\\] <--- UNKNOWN 200 \\([0-9]+ms\\)")
            }
          });
    }

    @MethodSource("data")
    @ParameterizedTest
    void httpProtocolVersion(
        Level logLevel, String protocolVersionName, List<String> expectedMessages) {
      initHttpProtocolVersionTest(logLevel, protocolVersionName, expectedMessages);
      server.enqueue(new MockResponse.Builder().status("HTTP/1.1 " + 200).build());

      SendsStuff api =
          Feign.builder()
              .client(new TestProtocolVersionClient(protocolVersionName))
              .logger(logger)
              .logLevel(logLevel)
              .target(SendsStuff.class, "http://localhost:" + server.getPort());

      api.login("netflix", "denominator", "password");
    }
  }

  @Nested
  public class ReadTimeoutEmitsTest {

    private Level logLevel;

    public void initReadTimeoutEmitsTest(Level logLevel, List<String> expectedMessages) {
      this.logLevel = logLevel;
      logger.expectMessages(expectedMessages);
    }

    public static Iterable<Object[]> data() {
      return Arrays.asList(
          new Object[][] {
            {Level.NONE, Collections.emptyList()},
            {
              Level.BASIC,
              Arrays.asList(
                  "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
                  "\\[SendsStuff#login\\] <--- ERROR SocketTimeoutException: Read timed out"
                      + " \\([0-9]+ms\\)")
            },
            {
              Level.HEADERS,
              Arrays.asList(
                  "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
                  "\\[SendsStuff#login\\] Content-Length: 80",
                  "\\[SendsStuff#login\\] Content-Type: application/json",
                  "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
                  "\\[SendsStuff#login\\] <--- ERROR SocketTimeoutException: Read timed out"
                      + " \\([0-9]+ms\\)")
            },
            {
              Level.FULL,
              Arrays.asList(
                  "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
                  "\\[SendsStuff#login\\] Content-Length: 80",
                  "\\[SendsStuff#login\\] Content-Type: application/json",
                  "\\[SendsStuff#login\\] ",
                  "\\[SendsStuff#login\\] \\{\"customer_name\": \"netflix\", \"user_name\":"
                      + " \"denominator\", \"password\": \"password\"\\}",
                  "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
                  "\\[SendsStuff#login\\] <--- ERROR SocketTimeoutException: Read timed out"
                      + " \\([0-9]+ms\\)",
                  "(?s)\\[SendsStuff#login\\] java.net.SocketTimeoutException: Read timed out.*",
                  "\\[SendsStuff#login\\] <--- END ERROR")
            }
          });
    }

    @MethodSource("data")
    @ParameterizedTest
    void levelEmitsOnReadTimeout(Level logLevel, List<String> expectedMessages) {
      initReadTimeoutEmitsTest(logLevel, expectedMessages);
      server.enqueue(
          new MockResponse.Builder().throttleBody(1, 1, TimeUnit.SECONDS).body("foo").build());

      SendsStuff api =
          Feign.builder()
              .logger(logger)
              .logLevel(logLevel)
              .options(
                  new Request.Options(
                      10 * 1000, TimeUnit.MILLISECONDS, 50, TimeUnit.MILLISECONDS, true))
              .retryer(
                  new Retryer() {
                    @Override
                    public void continueOrPropagate(RetryableException e) {
                      throw e;
                    }

                    @Override
                    public Retryer clone() {
                      return this;
                    }
                  })
              .target(SendsStuff.class, "http://localhost:" + server.getPort());

      assertThatThrownBy(() -> api.login("netflix", "denominator", "password"))
          .isInstanceOf(FeignException.class);
    }
  }

  @Nested
  public class UnknownHostEmitsTest {

    private Level logLevel;

    public void initUnknownHostEmitsTest(Level logLevel, List<String> expectedMessages) {
      this.logLevel = logLevel;
      logger.expectMessages(expectedMessages);
    }

    public static Iterable<Object[]> data() {
      return Arrays.asList(
          new Object[][] {
            {Level.NONE, Collections.emptyList()},
            {
              Level.BASIC,
              Arrays.asList(
                  "\\[SendsStuff#login\\] ---> POST http://non-exist.invalid/ HTTP/1.1",
                  "\\[SendsStuff#login\\] <--- ERROR UnknownHostException: non-exist.invalid"
                      + " \\([0-9]+ms\\)")
            },
            {
              Level.HEADERS,
              Arrays.asList(
                  "\\[SendsStuff#login\\] ---> POST http://non-exist.invalid/ HTTP/1.1",
                  "\\[SendsStuff#login\\] Content-Length: 80",
                  "\\[SendsStuff#login\\] Content-Type: application/json",
                  "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
                  "\\[SendsStuff#login\\] <--- ERROR UnknownHostException: non-exist.invalid"
                      + " \\([0-9]+ms\\)")
            },
            {
              Level.FULL,
              Arrays.asList(
                  "\\[SendsStuff#login\\] ---> POST http://non-exist.invalid/ HTTP/1.1",
                  "\\[SendsStuff#login\\] Content-Length: 80",
                  "\\[SendsStuff#login\\] Content-Type: application/json",
                  "\\[SendsStuff#login\\] ",
                  "\\[SendsStuff#login\\] \\{\"customer_name\": \"netflix\", \"user_name\":"
                      + " \"denominator\", \"password\": \"password\"\\}",
                  "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
                  "\\[SendsStuff#login\\] <--- ERROR UnknownHostException: non-exist.invalid"
                      + " \\([0-9]+ms\\)",
                  "(?s)\\[SendsStuff#login\\] java.net.UnknownHostException: non-exist.invalid.*",
                  "\\[SendsStuff#login\\] <--- END ERROR")
            }
          });
    }

    @MethodSource("data")
    @ParameterizedTest
    void unknownHostEmits(Level logLevel, List<String> expectedMessages) {
      initUnknownHostEmitsTest(logLevel, expectedMessages);
      SendsStuff api =
          Feign.builder()
              .logger(logger)
              .logLevel(logLevel)
              .retryer(
                  new Retryer() {
                    @Override
                    public void continueOrPropagate(RetryableException e) {
                      throw e;
                    }

                    @Override
                    public Retryer clone() {
                      return this;
                    }
                  })
              .target(SendsStuff.class, "http://non-exist.invalid");

      assertThatThrownBy(() -> api.login("netflix", "denominator", "password"))
          .isInstanceOf(FeignException.class);
    }
  }

  @Nested
  public class FormatCharacterTest {

    private Level logLevel;

    public void initFormatCharacterTest(Level logLevel, List<String> expectedMessages) {
      this.logLevel = logLevel;
      logger.expectMessages(expectedMessages);
    }

    public static Iterable<Object[]> data() {
      return Arrays.asList(
          new Object[][] {
            {Level.NONE, Collections.emptyList()},
            {
              Level.BASIC,
              Arrays.asList(
                  "\\[SendsStuff#login\\] ---> POST http://non-exist.invalid/ HTTP/1.1",
                  "\\[SendsStuff#login\\] <--- ERROR UnknownHostException: non-exist.invalid"
                      + " \\([0-9]+ms\\)")
            },
            {
              Level.HEADERS,
              Arrays.asList(
                  "\\[SendsStuff#login\\] ---> POST http://non-exist.invalid/ HTTP/1.1",
                  "\\[SendsStuff#login\\] Content-Length: 80",
                  "\\[SendsStuff#login\\] Content-Type: application/json",
                  "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
                  "\\[SendsStuff#login\\] <--- ERROR UnknownHostException: non-exist.invalid"
                      + " \\([0-9]+ms\\)")
            },
            {
              Level.FULL,
              Arrays.asList(
                  "\\[SendsStuff#login\\] ---> POST http://non-exist.invalid/ HTTP/1.1",
                  "\\[SendsStuff#login\\] Content-Length: 80",
                  "\\[SendsStuff#login\\] Content-Type: application/json",
                  "\\[SendsStuff#login\\] ",
                  "\\[SendsStuff#login\\] \\{\"customer_name\": \"netflix\", \"user_name\":"
                      + " \"denominator\", \"password\": \"password\"\\}",
                  "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
                  "\\[SendsStuff#login\\] <--- ERROR UnknownHostException: non-exist.invalid"
                      + " \\([0-9]+ms\\)",
                  "(?s)\\[SendsStuff#login\\] java.net.UnknownHostException: non-exist.invalid.*",
                  "\\[SendsStuff#login\\] <--- END ERROR")
            }
          });
    }

    @MethodSource("data")
    @ParameterizedTest
    void formatCharacterEmits(Level logLevel, List<String> expectedMessages) {
      initFormatCharacterTest(logLevel, expectedMessages);
      SendsStuff api =
          Feign.builder()
              .logger(logger)
              .logLevel(logLevel)
              .retryer(
                  new Retryer() {
                    @Override
                    public void continueOrPropagate(RetryableException e) {
                      throw e;
                    }

                    @Override
                    public Retryer clone() {
                      return this;
                    }
                  })
              .target(SendsStuff.class, "http://non-exist.invalid");

      assertThatThrownBy(() -> api.login("netflix", "denominator", "password"))
          .isInstanceOf(FeignException.class);
    }
  }

  @Nested
  public class RetryEmitsTest {

    private Level logLevel;

    public void initRetryEmitsTest(Level logLevel, List<String> expectedMessages) {
      this.logLevel = logLevel;
      logger.expectMessages(expectedMessages);
    }

    public static Iterable<Object[]> data() {
      return Arrays.asList(
          new Object[][] {
            {Level.NONE, Collections.emptyList()},
            {
              Level.BASIC,
              Arrays.asList(
                  "\\[SendsStuff#login\\] ---> POST http://non-exist.invalid/ HTTP/1.1",
                  "\\[SendsStuff#login\\] <--- ERROR UnknownHostException: non-exist.invalid"
                      + " \\([0-9]+ms\\)",
                  "\\[SendsStuff#login\\] ---> RETRYING",
                  "\\[SendsStuff#login\\] ---> POST http://non-exist.invalid/ HTTP/1.1",
                  "\\[SendsStuff#login\\] <--- ERROR UnknownHostException: non-exist.invalid"
                      + " \\([0-9]+ms\\)")
            }
          });
    }

    @MethodSource("data")
    @ParameterizedTest
    void retryEmits(Level logLevel, List<String> expectedMessages) {

      initRetryEmitsTest(logLevel, expectedMessages);

      SendsStuff api =
          Feign.builder()
              .logger(logger)
              .logLevel(logLevel)
              .retryer(
                  new Retryer() {
                    boolean retried;

                    @Override
                    public void continueOrPropagate(RetryableException e) {
                      if (!retried) {
                        retried = true;
                        return;
                      }
                      throw e;
                    }

                    @Override
                    public Retryer clone() {
                      return this;
                    }
                  })
              .target(SendsStuff.class, "http://non-exist.invalid");

      assertThatThrownBy(() -> api.login("netflix", "denominator", "password"))
          .isInstanceOf(FeignException.class);
    }
  }

  private static final class RecordingLogger extends Logger {

    private static final String PREFIX_X = "x-";
    private static final String PREFIX_Y = "y-";

    private final List<String> messages = new ArrayList<>();
    private final List<String> expectedMessages = new ArrayList<>();

    @Override
    protected boolean shouldLogRequestHeader(String header) {
      return !header.toLowerCase().startsWith(PREFIX_X);
    }

    @Override
    protected boolean shouldLogResponseHeader(String header) {
      return !header.toLowerCase().startsWith(PREFIX_Y);
    }

    void expectMessages(List<String> expectedMessages) {
      this.expectedMessages.addAll(expectedMessages);
    }

    @Override
    protected void log(String configKey, String format, Object... args) {
      messages.add(methodTag(configKey) + format.formatted(args));
    }

    // @Override
    // public Statement apply(final Statement base, Description description) {
    // return new Statement() {
    // @Override
    // public void evaluate() throws Throwable {
    // base.evaluate();
    // SoftAssertions softly = new SoftAssertions();
    // softly.assertThat(messages.size()).isEqualTo(expectedMessages.size());
    // for (int i = 0; i < messages.size() && i < expectedMessages.size(); i++) {
    // softly.assertThat(messages.get(i)).matches(expectedMessages.get(i));
    // }
    // softly.assertAll();
    // }
    // };
    // }
  }

  private static final class TestProtocolVersionClient extends DefaultClient {
    private final String protocolVersionName;

    public TestProtocolVersionClient(String protocolVersionName) {
      super(null, null);
      this.protocolVersionName = protocolVersionName;
    }

    @Override
    public Response convertResponse(HttpURLConnection connection, Request request)
        throws IOException {
      Response response = super.convertResponse(connection, request);
      if (nonNull((protocolVersionName))) {
        response =
            response.toBuilder()
                .protocolVersion(enumForName(ProtocolVersion.class, protocolVersionName))
                .build();
      }
      return response;
    }
  }
}
