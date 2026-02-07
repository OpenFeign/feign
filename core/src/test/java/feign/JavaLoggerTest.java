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

import feign.Logger.JavaLogger;
import feign.Request.HttpMethod;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JavaLoggerTest {

  private static final String CONFIG_KEY = "TestApi#testMethod()";
  private java.util.logging.Logger julLogger;
  private JavaLogger logger;

  @BeforeEach
  void setUp() {
    julLogger = java.util.logging.Logger.getLogger(JavaLoggerTest.class.getName());
    logger = new JavaLogger(JavaLoggerTest.class);
  }

  @Test
  void rebuffersResponseBodyWhenJulLevelIsInfo() throws Exception {
    // given
    julLogger.setLevel(Level.INFO);
    Response responseWithBody =
        Response.builder()
            .status(200)
            .reason("OK")
            .request(
                Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
            .headers(Collections.<String, Collection<String>>emptyMap())
            .body("{\"error\":\"test\"}", Util.UTF_8)
            .build();

    // when
    Response result =
        logger.logAndRebufferResponse(
            CONFIG_KEY, feign.Logger.Level.HEADERS, responseWithBody, 273);

    // then
    String body1 = Util.toString(result.body().asReader(Util.UTF_8));
    String body2 = Util.toString(result.body().asReader(Util.UTF_8));
    assert body1.equals("{\"error\":\"test\"}") : "First read should return body content";
    assert body2.equals("{\"error\":\"test\"}") : "Second read should return same body content";
  }

  @Test
  void rebuffersResponseBodyWhenJulLevelIsWarning() throws Exception {
    // given
    julLogger.setLevel(Level.WARNING);
    Response responseWithBody =
        Response.builder()
            .status(500)
            .reason("Internal Server Error")
            .request(
                Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
            .headers(Collections.<String, Collection<String>>emptyMap())
            .body("{\"message\":\"error details\"}", Util.UTF_8)
            .build();

    // when
    Response result =
        logger.logAndRebufferResponse(CONFIG_KEY, feign.Logger.Level.FULL, responseWithBody, 100);

    // then
    byte[] bodyBytes = Util.toByteArray(result.body().asInputStream());
    assert new String(bodyBytes, Util.UTF_8).equals("{\"message\":\"error details\"}")
        : "Body should be readable after rebuffering";
  }

  @Test
  void responseBodyReadableMultipleTimesForErrorDecoder() throws Exception {
    // given
    julLogger.setLevel(Level.SEVERE);
    String originalBody = "{\"errorCode\":\"E001\",\"message\":\"Validation failed\"}";
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

    // when
    Response result =
        logger.logAndRebufferResponse(
            CONFIG_KEY, feign.Logger.Level.HEADERS, responseWithBody, 150);

    // then
    String read1 = Util.toString(result.body().asReader(Util.UTF_8));
    String read2 = Util.toString(result.body().asReader(Util.UTF_8));
    String read3 = Util.toString(result.body().asReader(Util.UTF_8));
    assert read1.equals(originalBody) : "First read should match original body";
    assert read2.equals(originalBody) : "Second read should match original body";
    assert read3.equals(originalBody) : "Third read should match original body";
  }
}
