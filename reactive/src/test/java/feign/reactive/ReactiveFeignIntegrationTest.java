/*
 * Copyright 2012-2023 The Feign Authors
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
package feign.reactive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import feign.Client;
import feign.Logger;
import feign.Logger.Level;
import feign.Param;
import feign.QueryMap;
import feign.QueryMapEncoder;
import feign.Request;
import feign.Request.Options;
import feign.RequestInterceptor;
import feign.RequestLine;
import feign.RequestTemplate;
import feign.Response;
import feign.ResponseMapper;
import feign.RetryableException;
import feign.Retryer;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.jaxrs.JAXRSContract;
import io.reactivex.Flowable;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.AdditionalAnswers;
import org.mockito.stubbing.Answer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class ReactiveFeignIntegrationTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Rule
  public final MockWebServer webServer = new MockWebServer();

  private String getServerUrl() {
    return "http://localhost:" + this.webServer.getPort();
  }

  @Test
  public void testDefaultMethodsNotProxied() {
    TestReactorService service = ReactorFeign.builder()
        .target(TestReactorService.class, this.getServerUrl());
    assertThat(service).isEqualTo(service);
    assertThat(service.toString()).isNotNull();
    assertThat(service.hashCode()).isNotZero();
  }

  @Test
  public void testReactorTargetFull() throws Exception {
    this.webServer.enqueue(new MockResponse().setBody("1.0"));
    this.webServer.enqueue(new MockResponse().setBody("{ \"username\": \"test\" }"));

    TestReactorService service = ReactorFeign.builder()
        .encoder(new JacksonEncoder())
        .decoder(new JacksonDecoder())
        .logger(new ConsoleLogger())
        .dismiss404()
        .options(new Options())
        .logLevel(Level.FULL)
        .target(TestReactorService.class, this.getServerUrl());
    assertThat(service).isNotNull();

    StepVerifier.create(service.version())
        .expectNext("1.0")
        .expectComplete()
        .verify();
    assertThat(webServer.takeRequest().getPath()).isEqualToIgnoringCase("/version");


    /* test encoding and decoding */
    StepVerifier.create(service.user("test"))
        .assertNext(user -> assertThat(user).hasFieldOrPropertyWithValue("username", "test"))
        .expectComplete()
        .verify();
    assertThat(webServer.takeRequest().getPath()).isEqualToIgnoringCase("/users/test");

  }

  @Test
  public void testRxJavaTarget() throws Exception {
    this.webServer.enqueue(new MockResponse().setBody("1.0"));
    this.webServer.enqueue(new MockResponse().setBody("{ \"username\": \"test\" }"));

    TestReactiveXService service = RxJavaFeign.builder()
        .encoder(new JacksonEncoder())
        .decoder(new JacksonDecoder())
        .logger(new ConsoleLogger())
        .logLevel(Level.FULL)
        .target(TestReactiveXService.class, this.getServerUrl());
    assertThat(service).isNotNull();

    StepVerifier.create(service.version())
        .expectNext("1.0")
        .expectComplete()
        .verify();
    assertThat(webServer.takeRequest().getPath()).isEqualToIgnoringCase("/version");

    /* test encoding and decoding */
    StepVerifier.create(service.user("test"))
        .assertNext(user -> assertThat(user).hasFieldOrPropertyWithValue("username", "test"))
        .expectComplete()
        .verify();
    assertThat(webServer.takeRequest().getPath()).isEqualToIgnoringCase("/users/test");
  }

  @Test
  public void invocationFactoryIsNotSupported() {
    this.thrown.expect(UnsupportedOperationException.class);
    ReactorFeign.builder()
        .invocationHandlerFactory(
            (target, dispatch) -> null)
        .target(TestReactiveXService.class, "http://localhost");
  }

  @Test
  public void doNotCloseUnsupported() {
    this.thrown.expect(UnsupportedOperationException.class);
    ReactorFeign.builder()
        .doNotCloseAfterDecode()
        .target(TestReactiveXService.class, "http://localhost");
  }

  @Test
  public void testRequestInterceptor() {
    this.webServer.enqueue(new MockResponse().setBody("1.0"));

    RequestInterceptor mockInterceptor = mock(RequestInterceptor.class);
    TestReactorService service = ReactorFeign.builder()
        .requestInterceptor(mockInterceptor)
        .target(TestReactorService.class, this.getServerUrl());
    StepVerifier.create(service.version())
        .expectNext("1.0")
        .expectComplete()
        .verify();
    verify(mockInterceptor, times(1)).apply(any(RequestTemplate.class));
  }

  @Test
  public void testRequestInterceptors() {
    this.webServer.enqueue(new MockResponse().setBody("1.0"));

    RequestInterceptor mockInterceptor = mock(RequestInterceptor.class);
    TestReactorService service = ReactorFeign.builder()
        .requestInterceptors(Arrays.asList(mockInterceptor, mockInterceptor))
        .target(TestReactorService.class, this.getServerUrl());
    StepVerifier.create(service.version())
        .expectNext("1.0")
        .expectComplete()
        .verify();
    verify(mockInterceptor, times(2)).apply(any(RequestTemplate.class));
  }

  @Test
  public void testResponseMappers() throws Exception {
    this.webServer.enqueue(new MockResponse().setBody("1.0"));

    ResponseMapper responseMapper = mock(ResponseMapper.class);
    Decoder decoder = mock(Decoder.class);
    given(responseMapper.map(any(Response.class), any(Type.class)))
        .willAnswer(AdditionalAnswers.returnsFirstArg());
    given(decoder.decode(any(Response.class), any(Type.class))).willReturn("1.0");

    TestReactorService service = ReactorFeign.builder()
        .mapAndDecode(responseMapper, decoder)
        .target(TestReactorService.class, this.getServerUrl());
    StepVerifier.create(service.version())
        .expectNext("1.0")
        .expectComplete()
        .verify();
    verify(responseMapper, times(1))
        .map(any(Response.class), any(Type.class));
    verify(decoder, times(1)).decode(any(Response.class), any(Type.class));
  }

  @Test
  public void testQueryMapEncoders() {
    this.webServer.enqueue(new MockResponse().setBody("No Results Found"));

    QueryMapEncoder encoder = mock(QueryMapEncoder.class);
    given(encoder.encode(any(Object.class))).willReturn(Collections.emptyMap());
    TestReactiveXService service = RxJavaFeign.builder()
        .queryMapEncoder(encoder)
        .target(TestReactiveXService.class, this.getServerUrl());
    StepVerifier.create(service.search(new SearchQuery()))
        .expectNext("No Results Found")
        .expectComplete()
        .verify();
    verify(encoder, times(1)).encode(any(Object.class));
  }

  @SuppressWarnings({"ThrowableNotThrown"})
  @Test
  public void testErrorDecoder() {
    this.webServer.enqueue(new MockResponse().setBody("Bad Request").setResponseCode(400));

    ErrorDecoder errorDecoder = mock(ErrorDecoder.class);
    given(errorDecoder.decode(anyString(), any(Response.class)))
        .willReturn(new IllegalStateException("bad request"));

    TestReactiveXService service = RxJavaFeign.builder()
        .errorDecoder(errorDecoder)
        .target(TestReactiveXService.class, this.getServerUrl());
    StepVerifier.create(service.search(new SearchQuery()))
        .expectErrorSatisfies(ex -> assertThat(ex)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("bad request"))
        .verify();
    verify(errorDecoder, times(1)).decode(anyString(), any(Response.class));
  }

  @Test
  public void testRetryer() {
    this.webServer.enqueue(new MockResponse().setBody("Not Available").setResponseCode(-1));
    this.webServer.enqueue(new MockResponse().setBody("1.0"));

    Retryer retryer = new Retryer.Default();
    Retryer spy = spy(retryer);
    when(spy.clone()).thenReturn(spy);
    TestReactorService service = ReactorFeign.builder()
        .retryer(spy)
        .target(TestReactorService.class, this.getServerUrl());
    StepVerifier.create(service.version())
        .expectNext("1.0")
        .expectComplete()
        .verify();
    verify(spy, times(1)).continueOrPropagate(any(RetryableException.class));
  }

  @Test
  public void testClient() throws Exception {
    Client client = mock(Client.class);
    given(client.execute(any(Request.class), any(Options.class)))
        .willAnswer((Answer<Response>) invocation -> Response.builder()
            .status(200)
            .headers(Collections.emptyMap())
            .body("1.0", Charset.defaultCharset())
            .request((Request) invocation.getArguments()[0])
            .build());

    TestReactorService service = ReactorFeign.builder()
        .client(client)
        .target(TestReactorService.class, this.getServerUrl());
    StepVerifier.create(service.version())
        .expectNext("1.0")
        .expectComplete()
        .verify();
    verify(client, times(1)).execute(any(Request.class), any(Options.class));
  }

  @Test
  public void testDifferentContract() throws Exception {
    this.webServer.enqueue(new MockResponse().setBody("1.0"));

    TestJaxRSReactorService service = ReactorFeign.builder()
        .contract(new JAXRSContract())
        .target(TestJaxRSReactorService.class, this.getServerUrl());
    StepVerifier.create(service.version())
        .expectNext("1.0")
        .expectComplete()
        .verify();
    assertThat(webServer.takeRequest().getPath()).isEqualToIgnoringCase("/version");
  }


  interface TestReactorService {
    @RequestLine("GET /version")
    Mono<String> version();

    @RequestLine("GET /users/{username}")
    Flux<User> user(@Param("username") String username);
  }


  interface TestReactiveXService {
    @RequestLine("GET /version")
    Flowable<String> version();

    @RequestLine("GET /users/{username}")
    Flowable<User> user(@Param("username") String username);

    @RequestLine("GET /users/search")
    Flowable<String> search(@QueryMap SearchQuery query);
  }

  interface TestJaxRSReactorService {

    @Path("/version")
    @GET
    Mono<String> version();
  }


  @SuppressWarnings("unused")
  static class User {
    private String username;

    public User() {
      super();
    }

    public String getUsername() {
      return username;
    }
  }


  @SuppressWarnings("unused")
  static class SearchQuery {
    SearchQuery() {
      super();
    }

    public String query() {
      return "query";
    }
  }


  public static class ConsoleLogger extends Logger {
    @Override
    protected void log(String configKey, String format, Object... args) {
      System.out.println(String.format(methodTag(configKey) + format, args));
    }
  }
}
