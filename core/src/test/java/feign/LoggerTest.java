/*
 * Copyright 2013 Netflix, Inc.
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

import com.google.common.base.Joiner;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import dagger.Provides;
import feign.codec.Encoder;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@Test
public class LoggerTest {

  Logger logger = new Logger() {
    @Override protected void log(String configKey, String format, Object... args) {
      messages.add(methodTag(configKey) + String.format(format, args));
    }
  };

  List<String> messages = new ArrayList<String>();

  @BeforeMethod void clear() {
    messages.clear();
  }

  interface SendsStuff {

    @RequestLine("POST /")
    @Headers("Content-Type: application/json")
    @Body("%7B\"customer_name\": \"{customer_name}\", \"user_name\": \"{user_name}\", \"password\": \"{password}\"%7D")
    String login(
        @Named("customer_name") String customer,
        @Named("user_name") String user, @Named("password") String password);
  }

  @DataProvider(name = "levelToOutput")
  public Object[][] levelToOutput() {
    Object[][] data = new Object[4][2];
    data[0][0] = Logger.Level.NONE;
    data[0][1] = Arrays.<String>asList();
    data[1][0] = Logger.Level.BASIC;
    data[1][1] = Arrays.asList(
        "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
        "\\[SendsStuff#login\\] <--- HTTP/1.1 200 OK \\([0-9]+ms\\)"
    );
    data[2][0] = Logger.Level.HEADERS;
    data[2][1] = Arrays.asList(
        "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
        "\\[SendsStuff#login\\] Content-Type: application/json",
        "\\[SendsStuff#login\\] Content-Length: 80",
        "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
        "\\[SendsStuff#login\\] <--- HTTP/1.1 200 OK \\([0-9]+ms\\)",
        "\\[SendsStuff#login\\] Content-Length: 3",
        "\\[SendsStuff#login\\] <--- END HTTP \\(3-byte body\\)"
    );
    data[3][0] = Logger.Level.FULL;
    data[3][1] = Arrays.asList(
        "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
        "\\[SendsStuff#login\\] Content-Type: application/json",
        "\\[SendsStuff#login\\] Content-Length: 80",
        "\\[SendsStuff#login\\] ",
        "\\[SendsStuff#login\\] \\{\"customer_name\": \"netflix\", \"user_name\": \"denominator\", \"password\": \"password\"\\}",
        "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
        "\\[SendsStuff#login\\] <--- HTTP/1.1 200 OK \\([0-9]+ms\\)",
        "\\[SendsStuff#login\\] Content-Length: 3",
        "\\[SendsStuff#login\\] ",
        "\\[SendsStuff#login\\] foo",
        "\\[SendsStuff#login\\] <--- END HTTP \\(3-byte body\\)"
    );
    return data;
  }

  @Test(dataProvider = "levelToOutput")
  public void levelEmits(final Logger.Level logLevel, List<String> expectedMessages) throws IOException, InterruptedException {
    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBody("foo"));
    server.play();

    try {
      SendsStuff api = Feign.create(SendsStuff.class, "http://localhost:" + server.getUrl("").getPort(),
          new DefaultModule(logger, logLevel));

      api.login("netflix", "denominator", "password");

      assertEquals(messages.size(), expectedMessages.size());
      for (int i = 0; i < messages.size(); i++) {
        assertTrue(messages.get(i).matches(expectedMessages.get(i)), messages.get(i));
      }

      assertEquals(new String(server.takeRequest().getBody()),
          "{\"customer_name\": \"netflix\", \"user_name\": \"denominator\", \"password\": \"password\"}");
    } finally {
      server.shutdown();
    }
  }

  static @dagger.Module(overrides = true, library = true) class DefaultModule {
    final Logger logger;
    final Logger.Level logLevel;

    DefaultModule(Logger logger, Logger.Level logLevel) {
      this.logger = logger;
      this.logLevel = logLevel;
    }

    @Provides Encoder defaultEncoder() {
      return new Encoder() {
        @Override public void encode(Object object, RequestTemplate template) {
          template.body(object.toString());
        }
      };
    }

    @Provides @Singleton Logger logger() {
      return logger;
    }

    @Provides @Singleton Logger.Level level() {
      return logLevel;
    }
  }

  @DataProvider(name = "levelToReadTimeoutOutput")
  public Object[][] levelToReadTimeoutOutput() {
    Object[][] data = new Object[4][2];
    data[0][0] = Logger.Level.NONE;
    data[0][1] = Arrays.<String>asList();
    data[1][0] = Logger.Level.BASIC;
    data[1][1] = Arrays.asList(
        "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
        "\\[SendsStuff#login\\] <--- HTTP/1.1 200 OK \\([0-9]+ms\\)",
        "\\[SendsStuff#login\\] <--- ERROR SocketTimeoutException: Read timed out \\([0-9]+ms\\)"
    );
    data[2][0] = Logger.Level.HEADERS;
    data[2][1] = Arrays.asList(
        "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
        "\\[SendsStuff#login\\] Content-Type: application/json",
        "\\[SendsStuff#login\\] Content-Length: 80",
        "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
        "\\[SendsStuff#login\\] <--- HTTP/1.1 200 OK \\([0-9]+ms\\)",
        "\\[SendsStuff#login\\] Content-Length: 3",
        "\\[SendsStuff#login\\] <--- ERROR SocketTimeoutException: Read timed out \\([0-9]+ms\\)"
    );
    data[3][0] = Logger.Level.FULL;
    data[3][1] = Arrays.asList(
        "\\[SendsStuff#login\\] ---> POST http://localhost:[0-9]+/ HTTP/1.1",
        "\\[SendsStuff#login\\] Content-Type: application/json",
        "\\[SendsStuff#login\\] Content-Length: 80",
        "\\[SendsStuff#login\\] ",
        "\\[SendsStuff#login\\] \\{\"customer_name\": \"netflix\", \"user_name\": \"denominator\", \"password\": \"password\"\\}",
        "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
        "\\[SendsStuff#login\\] <--- HTTP/1.1 200 OK \\([0-9]+ms\\)",
        "\\[SendsStuff#login\\] Content-Length: 3",
        "\\[SendsStuff#login\\] ",
        "\\[SendsStuff#login\\] <--- ERROR SocketTimeoutException: Read timed out \\([0-9]+ms\\)",
        "\\[SendsStuff#login\\] java.net.SocketTimeoutException: Read timed out.*",
        "\\[SendsStuff#login\\] <--- END ERROR"
    );
    return data;
  }

  @dagger.Module(overrides = true, library = true)
  static class LessReadTimeoutModule {
    @Provides Request.Options lessReadTimeout() {
      return new Request.Options(10 * 1000, 50);
    }
  }

  @Test(dataProvider = "levelToReadTimeoutOutput")
  public void readTimeoutEmits(final Logger.Level logLevel, List<String> expectedMessages) throws IOException, InterruptedException {
    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBytesPerSecond(1).setBody("foo"));
    server.play();


    try {
      SendsStuff api = Feign.create(SendsStuff.class, "http://localhost:" + server.getUrl("").getPort(),
          new LessReadTimeoutModule(), new DefaultModule(logger, logLevel));

      api.login("netflix", "denominator", "password");

      fail();
    } catch (FeignException e) {

      assertMessagesMatch(expectedMessages);

      assertEquals(new String(server.takeRequest().getBody()),
          "{\"customer_name\": \"netflix\", \"user_name\": \"denominator\", \"password\": \"password\"}");
    } finally {
      server.shutdown();
    }
  }

  @DataProvider(name = "levelToUnknownHostOutput")
  public Object[][] levelToUnknownHostOutput() {
    Object[][] data = new Object[4][2];
    data[0][0] = Logger.Level.NONE;
    data[0][1] = Arrays.<String>asList();
    data[1][0] = Logger.Level.BASIC;
    data[1][1] = Arrays.asList(
        "\\[SendsStuff#login\\] ---> POST http://robofu.abc/ HTTP/1.1",
        "\\[SendsStuff#login\\] <--- ERROR UnknownHostException: robofu.abc \\([0-9]+ms\\)"
    );
    data[2][0] = Logger.Level.HEADERS;
    data[2][1] = Arrays.asList(
        "\\[SendsStuff#login\\] ---> POST http://robofu.abc/ HTTP/1.1",
        "\\[SendsStuff#login\\] Content-Type: application/json",
        "\\[SendsStuff#login\\] Content-Length: 80",
        "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
        "\\[SendsStuff#login\\] <--- ERROR UnknownHostException: robofu.abc \\([0-9]+ms\\)"
    );
    data[3][0] = Logger.Level.FULL;
    data[3][1] = Arrays.asList(
        "\\[SendsStuff#login\\] ---> POST http://robofu.abc/ HTTP/1.1",
        "\\[SendsStuff#login\\] Content-Type: application/json",
        "\\[SendsStuff#login\\] Content-Length: 80",
        "\\[SendsStuff#login\\] ",
        "\\[SendsStuff#login\\] \\{\"customer_name\": \"netflix\", \"user_name\": \"denominator\", \"password\": \"password\"\\}",
        "\\[SendsStuff#login\\] ---> END HTTP \\(80-byte body\\)",
        "\\[SendsStuff#login\\] <--- ERROR UnknownHostException: robofu.abc \\([0-9]+ms\\)",
        "\\[SendsStuff#login\\] java.net.UnknownHostException: robofu.abc.*",
        "\\[SendsStuff#login\\] <--- END ERROR"
    );
    return data;
  }

  @dagger.Module(overrides = true, library = true)
  static class DontRetryModule {
    @Provides Retryer retryer() {
      return new Retryer() {
        @Override public void continueOrPropagate(RetryableException e) {
          throw e;
        }
      };
    }
  }

  @Test(dataProvider = "levelToUnknownHostOutput")
  public void unknownHostEmits(final Logger.Level logLevel, List<String> expectedMessages) throws IOException, InterruptedException {

    try {
      SendsStuff api = Feign.create(SendsStuff.class, "http://robofu.abc",
          new DontRetryModule(), new DefaultModule(logger, logLevel));

      api.login("netflix", "denominator", "password");

      fail();
    } catch (FeignException e) {
      assertMessagesMatch(expectedMessages);
    }
  }

  @dagger.Module(overrides = true, library = true)
  static class RetryOnceModule {
    @Provides Retryer retryer() {
      return new Retryer() {
        boolean retried;

        @Override public void continueOrPropagate(RetryableException e) {
          if (!retried) {
            retried = true;
            return;
          }
          throw e;
        }
      };
    }
  }

  public void retryEmits() throws IOException, InterruptedException {

    try {
      SendsStuff api = Feign.create(SendsStuff.class, "http://robofu.abc",
          new RetryOnceModule(), new DefaultModule(logger, Logger.Level.BASIC));

      api.login("netflix", "denominator", "password");

      fail();
    } catch (FeignException e) {
      assertMessagesMatch(Arrays.asList(
          "\\[SendsStuff#login\\] ---> POST http://robofu.abc/ HTTP/1.1",
          "\\[SendsStuff#login\\] <--- ERROR UnknownHostException: robofu.abc \\([0-9]+ms\\)",
          "\\[SendsStuff#login\\] ---> RETRYING",
          "\\[SendsStuff#login\\] ---> POST http://robofu.abc/ HTTP/1.1",
          "\\[SendsStuff#login\\] <--- ERROR UnknownHostException: robofu.abc \\([0-9]+ms\\)"
      ));
    }
  }

  private void assertMessagesMatch(List<String> expectedMessages) {
    assertEquals(messages.size(), expectedMessages.size());
    for (int i = 0; i < messages.size(); i++) {
      assertTrue(Pattern.compile(expectedMessages.get(i), Pattern.DOTALL).matcher(messages.get(i)).matches(),
          "Didn't match at message " + (i + 1) + ":\n" + Joiner.on('\n').join(messages));
    }
  }
}
