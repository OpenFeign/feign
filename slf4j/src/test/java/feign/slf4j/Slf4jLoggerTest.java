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
package feign.slf4j;

import feign.Feign;
import feign.Logger;
import feign.Request;
import feign.Request.HttpMethod;
import feign.RequestTemplate;
import feign.Response;
import feign.Util;
import java.util.Collection;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.simple.RecordingSimpleLogger;

@SuppressWarnings("deprecation")
public class Slf4jLoggerTest {

  private static final String CONFIG_KEY = "someMethod()";
  private static final Request REQUEST =
      new RequestTemplate()
          .method(HttpMethod.GET)
          .target("http://api.example.com")
          .resolve(Collections.emptyMap())
          .request();
  private static final Response RESPONSE =
      Response.builder()
          .status(200)
          .reason("OK")
          .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
          .headers(Collections.<String, Collection<String>>emptyMap())
          .body(new byte[0])
          .build();
  public final RecordingSimpleLogger slf4j = new RecordingSimpleLogger();
  private Slf4jLogger logger;

  @Test
  void useFeignLoggerByDefault() throws Exception {
    slf4j.logLevel("debug");
    slf4j.expectMessages(
        "DEBUG feign.Logger - [someMethod] This is my message" + System.lineSeparator());

    logger = new Slf4jLogger();
    logger.log(CONFIG_KEY, "This is my message");
  }

  @Test
  void useLoggerByNameIfRequested() throws Exception {
    slf4j.logLevel("debug");
    slf4j.expectMessages(
        "DEBUG named.logger - [someMethod] This is my message" + System.lineSeparator());

    logger = new Slf4jLogger("named.logger");
    logger.log(CONFIG_KEY, "This is my message");
  }

  @Test
  void useLoggerByClassIfRequested() throws Exception {
    slf4j.logLevel("debug");
    slf4j.expectMessages(
        "DEBUG feign.Feign - [someMethod] This is my message" + System.lineSeparator());

    logger = new Slf4jLogger(Feign.class);
    logger.log(CONFIG_KEY, "This is my message");
  }

  @Test
  void useSpecifiedLoggerIfRequested() throws Exception {
    slf4j.logLevel("debug");
    slf4j.expectMessages(
        "DEBUG specified.logger - [someMethod] This is my message" + System.lineSeparator());

    logger = new Slf4jLogger(LoggerFactory.getLogger("specified.logger"));
    logger.log(CONFIG_KEY, "This is my message");
  }

  @Test
  void logOnlyIfDebugEnabled() throws Exception {
    slf4j.logLevel("info");

    logger = new Slf4jLogger();
    logger.log(CONFIG_KEY, "A message with %d formatting %s.", 2, "tokens");
    logger.logRequest(CONFIG_KEY, Logger.Level.BASIC, REQUEST);
    logger.logAndRebufferResponse(CONFIG_KEY, Logger.Level.BASIC, RESPONSE, 273);
  }

  @Test
  void logRequestsAndResponses() throws Exception {
    slf4j.logLevel("debug");
    slf4j.expectMessages(
        "DEBUG feign.Logger - [someMethod] A message with 2 formatting tokens."
            + System.lineSeparator()
            + "DEBUG feign.Logger - [someMethod] ---> GET http://api.example.com HTTP/1.1"
            + System.lineSeparator()
            + "DEBUG feign.Logger - [someMethod] <--- HTTP/1.1 200 OK (273ms)"
            + System.lineSeparator());

    logger = new Slf4jLogger();
    logger.log(CONFIG_KEY, "A message with %d formatting %s.", 2, "tokens");
    logger.logRequest(CONFIG_KEY, Logger.Level.BASIC, REQUEST);
    logger.logAndRebufferResponse(CONFIG_KEY, Logger.Level.BASIC, RESPONSE, 273);
  }

  @Test
  void rebuffersResponseBodyWhenLogLevelIsInfo() throws Exception {
    // given
    slf4j.logLevel("info");

    Response responseWithBody =
        Response.builder()
            .status(200)
            .reason("OK")
            .request(
                Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
            .headers(Collections.<String, Collection<String>>emptyMap())
            .body("{\"error\":\"test\"}", Util.UTF_8)
            .build();
    logger = new Slf4jLogger();

    // when
    Response result =
        logger.logAndRebufferResponse(CONFIG_KEY, Logger.Level.HEADERS, responseWithBody, 273);

    // then
    String body1 = Util.toString(result.body().asReader(Util.UTF_8));
    String body2 = Util.toString(result.body().asReader(Util.UTF_8));
    assert body1.equals("{\"error\":\"test\"}") : "First read should return body content";
    assert body2.equals("{\"error\":\"test\"}") : "Second read should return same body content";
  }

  @Test
  void rebuffersResponseBodyWhenLogLevelIsFull() throws Exception {
    // given
    slf4j.logLevel("info");
    Response responseWithBody =
        Response.builder()
            .status(500)
            .reason("Internal Server Error")
            .request(
                Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
            .headers(Collections.<String, Collection<String>>emptyMap())
            .body("{\"message\":\"error details\"}", Util.UTF_8)
            .build();
    logger = new Slf4jLogger();

    // when
    Response result =
        logger.logAndRebufferResponse(CONFIG_KEY, Logger.Level.FULL, responseWithBody, 100);

    // then
    byte[] bodyBytes = Util.toByteArray(result.body().asInputStream());
    assert new String(bodyBytes, Util.UTF_8).equals("{\"message\":\"error details\"}")
        : "Body should be readable after rebuffering";
  }

  @Test
  void responseBodyReadableMultipleTimes() throws Exception {
    // given
    slf4j.logLevel("info");
    String originalBody = "{\"data\":\"important information\",\"count\":42}";
    Response responseWithBody =
        Response.builder()
            .status(400)
            .reason("Bad Request")
            .request(
                Request.create(
                    HttpMethod.POST, "/api/submit", Collections.emptyMap(), null, Util.UTF_8))
            .headers(Collections.<String, Collection<String>>emptyMap())
            .body(originalBody, Util.UTF_8)
            .build();
    logger = new Slf4jLogger();

    // when
    Response result =
        logger.logAndRebufferResponse(CONFIG_KEY, Logger.Level.HEADERS, responseWithBody, 150);

    // then
    String read1 = Util.toString(result.body().asReader(Util.UTF_8));
    String read2 = Util.toString(result.body().asReader(Util.UTF_8));
    String read3 = Util.toString(result.body().asReader(Util.UTF_8));
    assert read1.equals(originalBody) : "First read should match original body";
    assert read2.equals(originalBody) : "Second read should match original body";
    assert read3.equals(originalBody) : "Third read should match original body";
  }
}
