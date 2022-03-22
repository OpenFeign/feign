/*
 * Copyright 2012-2022 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.slf4j;

import feign.MethodMetadata;
import feign.Request;
import feign.RetryableException;
import feign.Retryer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.RecordingSimpleLogger;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import static feign.Request.HttpMethod.HEAD;
import static feign.TestTools.methodMetadata;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.*;

public class LoggedRetryerTest {

  private Retryer delegate;
  private Retryer loggedRetryer;

  @Rule
  public final RecordingSimpleLogger slf4j = new RecordingSimpleLogger();

  @Before
  public void setUp() {
    delegate = mock(Retryer.class);
  }

  @Test
  public void noLogging() throws Exception {
    // given
    slf4j.logLevel("debug");
    loggedRetryer = Slf4jCapability.builder().build().enrich(delegate);
    MethodMetadata metadata = methodMetadata();
    metadata.configKey("testMethod(parameters)");
    Request request = Request.create(Request.HttpMethod.GET, "/home", Collections.emptyMap(),
        "data".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8, metadata.template());
    RetryableException exception = new RetryableException(-1, "test message", HEAD, null, request);
    // when
    loggedRetryer.continueOrPropagate(exception);
  }

  @Test
  public void noLoggingOnInfoLevel() throws Exception {
    // given
    slf4j.logLevel("info");
    loggedRetryer = Slf4jCapability.builder().withRetryer().build().enrich(delegate);
    MethodMetadata metadata = methodMetadata();
    metadata.configKey("testMethod(parameters)");
    Request request = Request.create(Request.HttpMethod.GET, "/home", Collections.emptyMap(),
        "data".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8, metadata.template());
    RetryableException exception = new RetryableException(-1, "test message", HEAD, null, request);
    // when
    loggedRetryer.continueOrPropagate(exception);
  }

  @Test
  public void testContinue() throws Exception {
    // given
    slf4j.logLevel("debug");
    slf4j.expectMessages("DEBUG feign.logger - [testMethod] ---> RETRYING");
    loggedRetryer = Slf4jCapability.builder().withRetryer().build().enrich(delegate);
    MethodMetadata metadata = methodMetadata();
    metadata.configKey("testMethod(parameters)");
    Request request = Request.create(Request.HttpMethod.GET, "/home", Collections.emptyMap(),
        "data".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8, metadata.template());
    RetryableException exception = new RetryableException(-1, "test message", HEAD, null, request);
    // when
    loggedRetryer.continueOrPropagate(exception);
  }

  @Test
  public void testPropagate() throws Exception {
    // given
    slf4j.logLevel("debug");
    loggedRetryer = Slf4jCapability.builder().withRetryer().build().enrich(delegate);
    MethodMetadata metadata = methodMetadata();
    metadata.configKey("testInterface#testMethod(parameters)");
    Request request = Request.create(Request.HttpMethod.GET, "/home", Collections.emptyMap(),
        "data".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8, null);
    RetryableException exception = new RetryableException(-1, "test message", HEAD, null, request);
    doThrow(exception).when(delegate).continueOrPropagate(isA(RetryableException.class));
    // when
    assertThrows(RetryableException.class, () -> loggedRetryer.continueOrPropagate(exception));
  }

  @Test
  public void customLogger() throws Exception {
    // given
    slf4j.logLevel("debug");
    slf4j.expectMessages("DEBUG test - [testMethod] ---> RETRYING");
    loggedRetryer =
        Slf4jCapability.builder().withRetryer().logger(LoggerFactory.getLogger("test")).build()
            .enrich(delegate);
    MethodMetadata metadata = methodMetadata();
    metadata.configKey("testMethod(parameters)");
    Request request = Request.create(Request.HttpMethod.GET, "/home", Collections.emptyMap(),
        "data".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8, metadata.template());
    RetryableException exception = new RetryableException(-1, "test message", HEAD, null, request);
    // when
    loggedRetryer.continueOrPropagate(exception);
  }

  @Test
  public void testClone() {
    loggedRetryer = Slf4jCapability.builder().withRetryer().build().enrich(delegate);
    assertNotSame("cloned retryer", loggedRetryer, loggedRetryer.clone());
  }

}
