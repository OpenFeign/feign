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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import feign.Request.HttpMethod;
import java.util.Collection;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class LoggerRebufferTest {

  private static final String CONFIG_KEY = "TestApi#testMethod()";

  private static class TestLogger extends Logger {
    @Override
    protected void log(String configKey, String format, Object... args) {}
  }

  @Test
  void headersLevelRebuffersResponseBody() throws Exception {
    // given
    TestLogger logger = new TestLogger();
    String originalBody = "{\"status\":\"error\",\"message\":\"Not found\"}";
    Response response =
        Response.builder()
            .status(404)
            .reason("Not Found")
            .request(
                Request.create(
                    HttpMethod.GET, "/api/resource", Collections.emptyMap(), null, Util.UTF_8))
            .headers(Collections.<String, Collection<String>>emptyMap())
            .body(originalBody, Util.UTF_8)
            .build();

    // when
    Response result =
        logger.logAndRebufferResponse(CONFIG_KEY, Logger.Level.HEADERS, response, 100);

    // then
    String read1 = Util.toString(result.body().asReader(Util.UTF_8));
    String read2 = Util.toString(result.body().asReader(Util.UTF_8));
    assertEquals(originalBody, read1, "First read should return original body");
    assertEquals(originalBody, read2, "Second read should return same body (rebuffered)");
  }

  @Test
  void basicLevelDoesNotRebufferResponseBody() throws Exception {
    // given
    TestLogger logger = new TestLogger();
    String originalBody = "{\"status\":\"ok\"}";
    Response response =
        Response.builder()
            .status(200)
            .reason("OK")
            .request(
                Request.create(
                    HttpMethod.GET, "/api/resource", Collections.emptyMap(), null, Util.UTF_8))
            .headers(Collections.<String, Collection<String>>emptyMap())
            .body(originalBody, Util.UTF_8)
            .build();

    // when
    Response result = logger.logAndRebufferResponse(CONFIG_KEY, Logger.Level.BASIC, response, 100);

    // then
    String read1 = Util.toString(result.body().asReader(Util.UTF_8));
    assertEquals(originalBody, read1, "First read should return original body");
  }

  @Test
  void fullLevelRebuffersResponseBody() throws Exception {
    // given
    TestLogger logger = new TestLogger();
    String originalBody = "{\"data\":{\"id\":123,\"name\":\"test\"}}";
    Response response =
        Response.builder()
            .status(200)
            .reason("OK")
            .request(
                Request.create(
                    HttpMethod.POST, "/api/create", Collections.emptyMap(), null, Util.UTF_8))
            .headers(Collections.<String, Collection<String>>emptyMap())
            .body(originalBody, Util.UTF_8)
            .build();

    // when
    Response result = logger.logAndRebufferResponse(CONFIG_KEY, Logger.Level.FULL, response, 50);

    // then
    String read1 = Util.toString(result.body().asReader(Util.UTF_8));
    String read2 = Util.toString(result.body().asReader(Util.UTF_8));
    String read3 = Util.toString(result.body().asReader(Util.UTF_8));
    assertEquals(originalBody, read1, "First read should return original body");
    assertEquals(originalBody, read2, "Second read should return same body");
    assertEquals(originalBody, read3, "Third read should return same body");
  }

  @Test
  void noneLevelDoesNotRebufferResponseBody() throws Exception {
    // given
    TestLogger logger = new TestLogger();
    String originalBody = "{\"result\":\"success\"}";
    Response response =
        Response.builder()
            .status(200)
            .reason("OK")
            .request(
                Request.create(
                    HttpMethod.GET, "/api/status", Collections.emptyMap(), null, Util.UTF_8))
            .headers(Collections.<String, Collection<String>>emptyMap())
            .body(originalBody, Util.UTF_8)
            .build();

    // When
    Response result = logger.logAndRebufferResponse(CONFIG_KEY, Logger.Level.NONE, response, 100);

    // then
    String read1 = Util.toString(result.body().asReader(Util.UTF_8));
    assertEquals(originalBody, read1, "Body should be readable");
  }

  @Test
  void http204DoesNotRebufferEvenAtHeadersLevel() throws Exception {
    // given
    TestLogger logger = new TestLogger();
    Response response =
        Response.builder()
            .status(204)
            .reason("No Content")
            .request(
                Request.create(
                    HttpMethod.DELETE, "/api/resource/1", Collections.emptyMap(), null, Util.UTF_8))
            .headers(Collections.<String, Collection<String>>emptyMap())
            .body("should be ignored", Util.UTF_8)
            .build();

    // when
    Response result =
        logger.logAndRebufferResponse(CONFIG_KEY, Logger.Level.HEADERS, response, 100);

    // then
    assertNotNull(result.body(), "Response body object should exist");
  }

  @Test
  void http205DoesNotRebufferEvenAtHeadersLevel() throws Exception {
    // given
    TestLogger logger = new TestLogger();
    Response response =
        Response.builder()
            .status(205)
            .reason("Reset Content")
            .request(
                Request.create(
                    HttpMethod.POST, "/api/form", Collections.emptyMap(), null, Util.UTF_8))
            .headers(Collections.<String, Collection<String>>emptyMap())
            .body("should be ignored", Util.UTF_8)
            .build();

    // when
    Response result =
        logger.logAndRebufferResponse(CONFIG_KEY, Logger.Level.HEADERS, response, 100);

    // then
    assertNotNull(result.body(), "Response body object should exist");
  }

  @Test
  void nullBodyHandledCorrectlyAtHeadersLevel() throws Exception {
    // given
    TestLogger logger = new TestLogger();
    Response response =
        Response.builder()
            .status(200)
            .reason("OK")
            .request(
                Request.create(
                    HttpMethod.HEAD, "/api/resource", Collections.emptyMap(), null, Util.UTF_8))
            .headers(Collections.<String, Collection<String>>emptyMap())
            .body((byte[]) null)
            .build();

    // when
    Response result =
        logger.logAndRebufferResponse(CONFIG_KEY, Logger.Level.HEADERS, response, 100);

    // then
    assertNull(result.body(), "Body should remain null");
  }
}
