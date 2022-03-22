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

import feign.Client;
import feign.MethodMetadata;
import feign.Request;
import feign.Response;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.impl.RecordingSimpleLogger;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static feign.TestTools.methodMetadata;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;

public class LoggedClientTest {

  private Slf4jCapability.LoggedClient loggedClient;
  private Client delegate;
  private MethodMetadata metadata;
  private Request request;
  private Request.Options options;
  private Response response;

  @Rule
  public final RecordingSimpleLogger slf4j = new RecordingSimpleLogger();

  @Before
  public void setUp() throws Exception {
    delegate = mock(Client.class);
    options = mock(Request.Options.class);

    metadata = methodMetadata();
    metadata.configKey("testInterface#testMethod(parameters)");
    request = Request.create(Request.HttpMethod.GET, "/home",
        Collections.singletonMap("accept", Collections.singleton("*/*")),
        "request body".getBytes(UTF_8), UTF_8, metadata.template());
    response = Response.builder().status(200).reason("OK").request(request).headers(
        Collections.singletonMap("content-type", Collections.singleton("application/json")))
        .body("response body", UTF_8).build();

    when(delegate.execute(isA(Request.class), isA(Request.Options.class))).thenReturn(response);
  }

  @Test
  public void noLoggingOnInfoLevel() throws Exception {
    // given
    slf4j.logLevel("info");
    loggedClient =
        (Slf4jCapability.LoggedClient) Slf4jCapability.builder().withHeaders().withRequest()
            .withResponse().withStacktrace().build().enrich(delegate);
    // when
    Response notLoggedResponse = loggedClient.execute(request, options);
    // then
    assertSame(response, notLoggedResponse);
  }

  @Test
  public void fullLogging() throws Exception {
    // given
    slf4j.logLevel("debug");
    slf4j.expectMessages("DEBUG feign.logger - [testInterface#testMethod] ---> GET /home HTTP/1.1",
        "DEBUG feign.logger - [testInterface#testMethod] accept: */*",
        "DEBUG feign.logger - [testInterface#testMethod] ",
        "DEBUG feign.logger - [testInterface#testMethod] request body",
        "DEBUG feign.logger - [testInterface#testMethod] ---> END HTTP (12-byte body)",
        "DEBUG feign.logger - [testInterface#testMethod] <--- HTTP/1.1 200 OK (123ms)",
        "DEBUG feign.logger - [testInterface#testMethod] content-type: application/json",
        "DEBUG feign.logger - [testInterface#testMethod] ",
        "DEBUG feign.logger - [testInterface#testMethod] response body",
        "DEBUG feign.logger - [testInterface#testMethod] <--- END HTTP (13-byte body)");
    loggedClient =
        spy((Slf4jCapability.LoggedClient) Slf4jCapability.builder().withHeaders().withRequest()
            .withResponse().withStacktrace().build().enrich(delegate));
    when(loggedClient.elapsedTime(anyLong())).thenReturn(123L);
    // when
    Response loggedResponse = loggedClient.execute(request, options);
    // then
    assertNotSame(response, loggedResponse);
  }

  @Test
  public void basicLogging() throws Exception {
    // given
    slf4j.logLevel("debug");
    slf4j.expectMessages("DEBUG feign.logger - [testInterface#testMethod] ---> GET /home HTTP/1.1",
        "DEBUG feign.logger - [testInterface#testMethod] ---> END HTTP (12-byte body)",
        "DEBUG feign.logger - [testInterface#testMethod] <--- HTTP/1.1 200 OK (123ms)",
        "DEBUG feign.logger - [testInterface#testMethod] <--- END HTTP (13-byte body)");
    loggedClient =
        spy((Slf4jCapability.LoggedClient) Slf4jCapability.builder().build().enrich(delegate));
    when(loggedClient.elapsedTime(anyLong())).thenReturn(123L);
    // when
    Response loggedResponse = loggedClient.execute(request, options);
    // then
    assertSame(response, loggedResponse);
  }

  @Test
  public void basicExceptionLogging() throws Exception {
    // given
    slf4j.logLevel("debug");
    slf4j.expectMessages("DEBUG feign.logger - [testInterface#testMethod] ---> GET /home HTTP/1.1",
        "DEBUG feign.logger - [testInterface#testMethod] ---> END HTTP (12-byte body)",
        "DEBUG feign.logger - [testInterface#testMethod] <--- ERROR IOException: test exception (123ms)");
    loggedClient =
        spy((Slf4jCapability.LoggedClient) Slf4jCapability.builder().build().enrich(delegate));
    when(delegate.execute(isA(Request.class), isA(Request.Options.class))).thenThrow(
        new IOException("test exception"));
    when(loggedClient.elapsedTime(anyLong())).thenReturn(123L);
    // when
    assertThrows(IOException.class, () -> loggedClient.execute(request, options));
  }

  @Test
  public void headerLogging() throws Exception {
    // given
    slf4j.logLevel("debug");
    slf4j.expectMessages("DEBUG feign.logger - [testInterface#testMethod] ---> GET /home HTTP/1.1",
        "DEBUG feign.logger - [testInterface#testMethod] accept: */*",
        "DEBUG feign.logger - [testInterface#testMethod] ---> END HTTP (12-byte body)",
        "DEBUG feign.logger - [testInterface#testMethod] <--- HTTP/1.1 200 OK (123ms)",
        "DEBUG feign.logger - [testInterface#testMethod] content-type: application/json",
        "DEBUG feign.logger - [testInterface#testMethod] <--- END HTTP (13-byte body)");
    loggedClient =
        spy((Slf4jCapability.LoggedClient) Slf4jCapability.builder().withHeaders().build()
            .enrich(delegate));
    when(loggedClient.elapsedTime(anyLong())).thenReturn(123L);
    // when
    Response loggedResponse = loggedClient.execute(request, options);
    // then
    assertSame(response, loggedResponse);
  }

  @Test
  public void allowedHeaderLogging() throws Exception {
    // given
    slf4j.logLevel("debug");
    slf4j.expectMessages("DEBUG feign.logger - [testInterface#testMethod] ---> GET /home HTTP/1.1",
        "DEBUG feign.logger - [testInterface#testMethod] AccEpT: text/html",
        "DEBUG feign.logger - [testInterface#testMethod] AccEpT: application/json",
        "DEBUG feign.logger - [testInterface#testMethod] AccEpT: text/plain",
        "DEBUG feign.logger - [testInterface#testMethod] ---> END HTTP (12-byte body)",
        "DEBUG feign.logger - [testInterface#testMethod] <--- HTTP/1.1 200 OK (123ms)",
        "DEBUG feign.logger - [testInterface#testMethod] content-type: application/json",
        "DEBUG feign.logger - [testInterface#testMethod] <--- END HTTP (13-byte body)");
    Map<String, Collection<String>> requestHeaders = new HashMap<>();
    requestHeaders.put("AccEpT", asList("text/html", "application/json", "text/plain"));
    requestHeaders.put("HOST", Collections.singleton("localhost"));
    requestHeaders.put("User-Agent", Collections.singleton("unit test"));
    Map<String, Collection<String>> responseHeaders = new HashMap<>();
    responseHeaders.put("Content-Type", Collections.singleton("application/json"));
    responseHeaders.put("Server", Collections.singleton("test server"));
    request = Request.create(Request.HttpMethod.GET, "/home", requestHeaders,
        "request body".getBytes(UTF_8), UTF_8, metadata.template());
    response = Response.builder().status(200).reason("OK").request(request).headers(responseHeaders)
        .body("response body", UTF_8).build();
    loggedClient = spy((Slf4jCapability.LoggedClient) Slf4jCapability.builder()
        .withAllowedHeaders("accept", "content-type").build().enrich(delegate));
    when(delegate.execute(isA(Request.class), isA(Request.Options.class))).thenReturn(response);
    when(loggedClient.elapsedTime(anyLong())).thenReturn(123L);
    // when
    Response loggedResponse = loggedClient.execute(request, options);
    // then
    assertSame(response, loggedResponse);
  }

  @Test
  public void deniedHeaderLogging() throws Exception {
    // given
    slf4j.logLevel("debug");
    slf4j.expectMessages("DEBUG feign.logger - [testInterface#testMethod] ---> GET /home HTTP/1.1",
        "DEBUG feign.logger - [testInterface#testMethod] User-Agent: unit test",
        "DEBUG feign.logger - [testInterface#testMethod] HOST: localhost",
        "DEBUG feign.logger - [testInterface#testMethod] ---> END HTTP (12-byte body)",
        "DEBUG feign.logger - [testInterface#testMethod] <--- HTTP/1.1 200 OK (123ms)",
        "DEBUG feign.logger - [testInterface#testMethod] server: test server",
        "DEBUG feign.logger - [testInterface#testMethod] <--- END HTTP (13-byte body)");
    Map<String, Collection<String>> requestHeaders = new HashMap<>();
    requestHeaders.put("AccEpT", asList("text/html", "application/json", "text/plain"));
    requestHeaders.put("HOST", Collections.singleton("localhost"));
    requestHeaders.put("User-Agent", Collections.singleton("unit test"));
    Map<String, Collection<String>> responseHeaders = new HashMap<>();
    responseHeaders.put("Content-Type", Collections.singleton("application/json"));
    responseHeaders.put("Server", Collections.singleton("test server"));
    request = Request.create(Request.HttpMethod.GET, "/home", requestHeaders,
        "request body".getBytes(UTF_8), UTF_8, metadata.template());
    response = Response.builder().status(200).reason("OK").request(request).headers(responseHeaders)
        .body("response body", UTF_8).build();
    loggedClient = spy((Slf4jCapability.LoggedClient) Slf4jCapability.builder()
        .withDeniedHeaders("accept", "content-type").build().enrich(delegate));
    when(delegate.execute(isA(Request.class), isA(Request.Options.class))).thenReturn(response);
    when(loggedClient.elapsedTime(anyLong())).thenReturn(123L);
    // when
    Response loggedResponse = loggedClient.execute(request, options);
    // then
    assertSame(response, loggedResponse);
  }

  @Test
  public void combinedHeaderLogging() throws Exception {
    // given
    slf4j.logLevel("debug");
    slf4j.expectMessages("DEBUG feign.logger - [testInterface#testMethod] ---> GET /home HTTP/1.1",
        "DEBUG feign.logger - [testInterface#testMethod] HOST: localhost",
        "DEBUG feign.logger - [testInterface#testMethod] ---> END HTTP (12-byte body)",
        "DEBUG feign.logger - [testInterface#testMethod] <--- HTTP/1.1 200 OK (123ms)",
        "DEBUG feign.logger - [testInterface#testMethod] server: test server",
        "DEBUG feign.logger - [testInterface#testMethod] <--- END HTTP (13-byte body)");
    Map<String, Collection<String>> requestHeaders = new HashMap<>();
    requestHeaders.put("AccEpT", asList("text/html", "application/json", "text/plain"));
    requestHeaders.put("HOST", Collections.singleton("localhost"));
    requestHeaders.put("User-Agent", Collections.singleton("unit test"));
    Map<String, Collection<String>> responseHeaders = new HashMap<>();
    responseHeaders.put("Content-Type", Collections.singleton("application/json"));
    responseHeaders.put("Server", Collections.singleton("test server"));
    request = Request.create(Request.HttpMethod.GET, "/home", requestHeaders,
        "request body".getBytes(UTF_8), UTF_8, metadata.template());
    response = Response.builder().status(200).reason("OK").request(request).headers(responseHeaders)
        .body("response body", UTF_8).build();
    loggedClient = spy((Slf4jCapability.LoggedClient) Slf4jCapability.builder()
        .withAllowedHeaders("SeRvEr", "host").withDeniedHeaders("accept", "content-type").build()
        .enrich(delegate));
    when(delegate.execute(isA(Request.class), isA(Request.Options.class))).thenReturn(response);
    when(loggedClient.elapsedTime(anyLong())).thenReturn(123L);
    // when
    Response loggedResponse = loggedClient.execute(request, options);
    // then
    assertSame(response, loggedResponse);
  }

  @Test
  public void requestLogging() throws Exception {
    // given
    slf4j.logLevel("debug");
    slf4j.expectMessages("DEBUG feign.logger - [testInterface#testMethod] ---> GET /home HTTP/1.1",
        "DEBUG feign.logger - [testInterface#testMethod] ",
        "DEBUG feign.logger - [testInterface#testMethod] request body",
        "DEBUG feign.logger - [testInterface#testMethod] ---> END HTTP (12-byte body)",
        "DEBUG feign.logger - [testInterface#testMethod] <--- HTTP/1.1 200 OK (123ms)",
        "DEBUG feign.logger - [testInterface#testMethod] <--- END HTTP (13-byte body)");
    loggedClient =
        spy((Slf4jCapability.LoggedClient) Slf4jCapability.builder().withRequest().build()
            .enrich(delegate));
    when(loggedClient.elapsedTime(anyLong())).thenReturn(123L);
    // when
    Response loggedResponse = loggedClient.execute(request, options);
    // then
    assertSame(response, loggedResponse);
  }

  @Test
  public void responseLogging() throws Exception {
    // given
    slf4j.logLevel("debug");
    slf4j.expectMessages("DEBUG feign.logger - [testInterface#testMethod] ---> GET /home HTTP/1.1",
        "DEBUG feign.logger - [testInterface#testMethod] ---> END HTTP (12-byte body)",
        "DEBUG feign.logger - [testInterface#testMethod] <--- HTTP/1.1 200 OK (123ms)",
        "DEBUG feign.logger - [testInterface#testMethod] ",
        "DEBUG feign.logger - [testInterface#testMethod] response body",
        "DEBUG feign.logger - [testInterface#testMethod] <--- END HTTP (13-byte body)");
    loggedClient =
        spy((Slf4jCapability.LoggedClient) Slf4jCapability.builder().withResponse().build()
            .enrich(delegate));
    when(loggedClient.elapsedTime(anyLong())).thenReturn(123L);
    // when
    Response loggedResponse = loggedClient.execute(request, options);
    // then
    assertNotSame(response, loggedResponse);
  }

  @Test
  public void stacktraceLogging() throws Exception {
    // given
    slf4j.logLevel("debug");
    slf4j.expectMessages("DEBUG feign.logger - [testInterface#testMethod] ---> GET /home HTTP/1.1",
        "DEBUG feign.logger - [testInterface#testMethod] ---> END HTTP (12-byte body)",
        "DEBUG feign.logger - [testInterface#testMethod] <--- ERROR IOException: test exception (123ms)",
        "DEBUG feign.logger - [testInterface#testMethod] java.io.IOException: test exception",
        "\tat feign.slf4j.Slf4jCapability$LoggedClient.execute(Slf4jCapability.java:269)",
        "\tat feign.slf4j.LoggedClientTest.lambda$stacktraceLogging$1(LoggedClientTest.java:309)");
    loggedClient =
        spy((Slf4jCapability.LoggedClient) Slf4jCapability.builder().withStacktrace().build()
            .enrich(delegate));
    when(delegate.execute(isA(Request.class), isA(Request.Options.class))).thenThrow(
        new IOException("test exception"));
    when(loggedClient.elapsedTime(anyLong())).thenReturn(123L);
    // when
    assertThrows(IOException.class, () -> loggedClient.execute(request, options));
  }

}
