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
package feign.slf4j;

import static org.testng.Assert.assertEquals;

import feign.Feign;
import feign.Logger;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import feign.Util;
import java.io.File;
import java.io.FileReader;
import java.util.Collection;
import java.util.Collections;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

public class Slf4jLoggerTest {
  private static final String CONFIG_KEY = "someMethod()";
  private static final Request REQUEST =
      new RequestTemplate().method("GET").append("http://api.example.com").request();
  private static final Response RESPONSE =
      Response.create(200, "OK", Collections.<String, Collection<String>>emptyMap(), new byte[0]);

  private File logFile;
  private Slf4jLogger logger;

  @AfterMethod
  void tearDown() throws Exception {
    SimpleLoggerUtil.resetToDefaults();
    logFile.delete();
  }

  @Test
  public void useFeignLoggerByDefault() throws Exception {
    initializeSimpleLogger("debug");
    logger = new Slf4jLogger();
    logger.log(CONFIG_KEY, "This is my message");
    assertLoggedMessages("DEBUG feign.Logger - [someMethod] This is my message\n");
  }

  @Test
  public void useLoggerByNameIfRequested() throws Exception {
    initializeSimpleLogger("debug");
    logger = new Slf4jLogger("named.logger");
    logger.log(CONFIG_KEY, "This is my message");
    assertLoggedMessages("DEBUG named.logger - [someMethod] This is my message\n");
  }

  @Test
  public void useLoggerByClassIfRequested() throws Exception {
    initializeSimpleLogger("debug");
    logger = new Slf4jLogger(Feign.class);
    logger.log(CONFIG_KEY, "This is my message");
    assertLoggedMessages("DEBUG feign.Feign - [someMethod] This is my message\n");
  }

  @Test
  public void useSpecifiedLoggerIfRequested() throws Exception {
    initializeSimpleLogger("debug");
    logger = new Slf4jLogger(LoggerFactory.getLogger("specified.logger"));
    logger.log(CONFIG_KEY, "This is my message");
    assertLoggedMessages("DEBUG specified.logger - [someMethod] This is my message\n");
  }

  @Test
  public void logOnlyIfDebugEnabled() throws Exception {
    initializeSimpleLogger("info");
    logger = new Slf4jLogger();
    logger.log(CONFIG_KEY, "A message with %d formatting %s.", 2, "tokens");
    logger.logRequest(CONFIG_KEY, Logger.Level.BASIC, REQUEST);
    logger.logAndRebufferResponse(CONFIG_KEY, Logger.Level.BASIC, RESPONSE, 273);
    assertLoggedMessages("");
  }

  @Test
  public void logRequestsAndResponses() throws Exception {
    initializeSimpleLogger("debug");
    logger = new Slf4jLogger();
    logger.log(CONFIG_KEY, "A message with %d formatting %s.", 2, "tokens");
    logger.logRequest(CONFIG_KEY, Logger.Level.BASIC, REQUEST);
    logger.logAndRebufferResponse(CONFIG_KEY, Logger.Level.BASIC, RESPONSE, 273);
    assertLoggedMessages(
        "DEBUG feign.Logger - [someMethod] A message with 2 formatting tokens.\n"
            + "DEBUG feign.Logger - [someMethod] ---> GET http://api.example.com HTTP/1.1\n"
            + "DEBUG feign.Logger - [someMethod] <--- HTTP/1.1 200 OK (273ms)\n");
  }

  private void initializeSimpleLogger(String logLevel) throws Exception {
    logFile = File.createTempFile(getClass().getName(), ".log");
    SimpleLoggerUtil.initialize(logFile, logLevel);
  }

  private void assertLoggedMessages(String expectedMessages) throws Exception {
    assertEquals(Util.toString(new FileReader(logFile)), expectedMessages);
  }
}
