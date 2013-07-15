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

import com.google.common.collect.ImmutableMap;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import dagger.Provides;
import feign.codec.Decoder;
import feign.codec.StringDecoder;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Test
public class LoggerTest {

  Logger logger = new Logger() {
    @Override protected void log(Target<?> target, String format, Object... args) {
      messages.add(String.format(format, args));
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
  public Object[][] createData() {
    Object[][] data = new Object[4][2];
    data[0][0] = Logger.Level.NONE;
    data[0][1] = Arrays.<String>asList();
    data[1][0] = Logger.Level.BASIC;
    data[1][1] = Arrays.asList(
        "---> POST http://localhost:[0-9]+/ HTTP/1.1",
        "<--- HTTP/1.1 200 OK \\([0-9]+ms\\)"
    );
    data[2][0] = Logger.Level.HEADERS;
    data[2][1] = Arrays.asList(
        "---> POST http://localhost:[0-9]+/ HTTP/1.1",
        "Content-Type: application/json",
        "Content-Length: 80",
        "---> END HTTP \\(80-byte body\\)",
        "<--- HTTP/1.1 200 OK \\([0-9]+ms\\)",
        "Content-Length: 3",
        "<--- END HTTP \\(3-byte body\\)"
    );
    data[3][0] = Logger.Level.FULL;
    data[3][1] = Arrays.asList(
        "---> POST http://localhost:[0-9]+/ HTTP/1.1",
        "Content-Type: application/json",
        "Content-Length: 80",
        "",
        "\\{\"customer_name\": \"netflix\", \"user_name\": \"denominator\", \"password\": \"password\"\\}",
        "---> END HTTP \\(80-byte body\\)",
        "<--- HTTP/1.1 200 OK \\([0-9]+ms\\)",
        "Content-Length: 3",
        "",
        "foo",
        "<--- END HTTP \\(3-byte body\\)"
    );
    return data;
  }

  @Test(dataProvider = "levelToOutput")
  public void levelEmits(final Logger.Level logLevel, List<String> expectedMessages) throws IOException, InterruptedException {
    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setResponseCode(200).setBody("foo"));
    server.play();

    @dagger.Module(overrides = true, library = true) class Module {
      @Provides @Singleton Map<String, Decoder> decoders() {
        return ImmutableMap.<String, Decoder>of("SendsStuff", new StringDecoder());
      }

      @Provides @Singleton Logger logger() {
        return logger;
      }

      @Provides @Singleton Logger.Level level() {
        return logLevel;
      }
    }

    try {
      SendsStuff api = Feign.create(SendsStuff.class, "http://localhost:" + server.getUrl("").getPort(), new Module());

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
}
