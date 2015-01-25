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
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;
import com.squareup.okhttp.mockwebserver.SocketPolicy;
import com.squareup.okhttp.mockwebserver.rule.MockWebServerRule;
import dagger.Module;
import dagger.Provides;
import feign.Target.HardCodedTarget;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.codec.StringDecoder;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static dagger.Provides.Type.SET;
import static feign.Util.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

// unbound wildcards are not currently injectable in dagger.
@SuppressWarnings("rawtypes")
public class FeignTest {
  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Rule public final MockWebServerRule server = new MockWebServerRule();

  interface TestInterface {
    @RequestLine("POST /") Response response();

    @RequestLine("POST /") String post();

    @RequestLine("POST /")
    @Body("%7B\"customer_name\": \"{customer_name}\", \"user_name\": \"{user_name}\", \"password\": \"{password}\"%7D")
    void login(
        @Named("customer_name") String customer, @Named("user_name") String user, @Named("password") String password);

    @RequestLine("POST /") void body(List<String> contents);

    @RequestLine("POST /") @Headers("Content-Encoding: gzip") void gzipBody(List<String> contents);

    @RequestLine("POST /") void form(
        @Named("customer_name") String customer, @Named("user_name") String user, @Named("password") String password);

    @RequestLine("GET /{1}/{2}") Response uriParam(@Named("1") String one, URI endpoint, @Named("2") String two);

    @RequestLine("GET /?1={1}&2={2}") Response queryParams(@Named("1") String one, @Named("2") Iterable<String> twos);

    @dagger.Module(injects = Feign.class, addsTo = Feign.Defaults.class)
    static class Module {
      @Provides Decoder defaultDecoder() {
        return new Decoder.Default();
      }

      @Provides Encoder defaultEncoder() {
        return new Encoder() {
          @Override public void encode(Object object, RequestTemplate template) {
            if (object instanceof Map) {
              template.body(Joiner.on(',').withKeyValueSeparator("=").join((Map) object));
            } else {
              template.body(object.toString());
            }
          }
        };
      }
    }
  }

  @Test
  public void iterableQueryParams() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api = Feign.create(TestInterface.class, "http://localhost:" + server.getPort(), new TestInterface.Module());

    api.queryParams("user", Arrays.asList("apple", "pear"));
    assertEquals("GET /?1=user&2=apple&2=pear HTTP/1.1", server.takeRequest().getRequestLine());
  }

  interface OtherTestInterface {
    @RequestLine("POST /") String post();

    @RequestLine("POST /") byte[] binaryResponseBody();

    @RequestLine("POST /") void binaryRequestBody(byte[] contents);
  }

  @Module(library = true, overrides = true)
  static class RunSynchronous {
    @Provides @Singleton @Named("http") Executor httpExecutor() {
      return new Executor() {
        @Override public void execute(Runnable command) {
          command.run();
        }
      };
    }
  }

  @Test
  public void postTemplateParamsResolve() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api = Feign.create(TestInterface.class, "http://localhost:" + server.getPort(), new TestInterface.Module());

    api.login("netflix", "denominator", "password");
    assertEquals("{\"customer_name\": \"netflix\", \"user_name\": \"denominator\", \"password\": \"password\"}",
        server.takeRequest().getUtf8Body());
  }

  @Test
  public void responseCoercesToStringBody() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api = Feign.create(TestInterface.class, "http://localhost:" + server.getPort(),
        new TestInterface.Module());

    Response response = api.response();
    assertTrue(response.body().isRepeatable());
    assertEquals("foo", response.body().toString());
  }

  @Test
  public void postFormParams() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api = Feign.create(TestInterface.class, "http://localhost:" + server.getPort(), new TestInterface.Module());

    api.form("netflix", "denominator", "password");
    assertEquals("customer_name=netflix,user_name=denominator,password=password",
        server.takeRequest().getUtf8Body());
  }

  @Test
  public void postBodyParam() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api = Feign.create(TestInterface.class, "http://localhost:" + server.getPort(), new TestInterface.Module());

    api.body(Arrays.asList("netflix", "denominator", "password"));
    RecordedRequest request = server.takeRequest();
    assertEquals("32", request.getHeader("Content-Length"));
    assertEquals("[netflix, denominator, password]", request.getUtf8Body());
  }

  @Test
  public void postGZIPEncodedBodyParam() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api = Feign.create(TestInterface.class, "http://localhost:" + server.getPort(), new TestInterface.Module());

    api.gzipBody(Arrays.asList("netflix", "denominator", "password"));
    RecordedRequest request = server.takeRequest();
    assertNull(request.getHeader("Content-Length"));
    byte[] compressedBody = request.getBody();
    String uncompressedBody = CharStreams.toString(CharStreams.newReaderSupplier(
        GZIPStreams.newInputStreamSupplier(ByteStreams.newInputStreamSupplier(compressedBody)), UTF_8));
    assertEquals("[netflix, denominator, password]", uncompressedBody);
  }

  @Module(library = true)
  static class ForwardedForInterceptor implements RequestInterceptor {
    @Provides(type = SET) RequestInterceptor provideThis() {
      return this;
    }

    @Override public void apply(RequestTemplate template) {
      template.header("X-Forwarded-For", "origin.host.com");
    }
  }

  @Test
  public void singleInterceptor() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api = Feign.create(TestInterface.class, "http://localhost:" + server.getPort(),
        new TestInterface.Module(), new ForwardedForInterceptor());

    api.post();
    assertEquals("origin.host.com", server.takeRequest().getHeader("X-Forwarded-For"));
  }

  @Module(library = true)
  static class UserAgentInterceptor implements RequestInterceptor {
    @Provides(type = SET) RequestInterceptor provideThis() {
      return this;
    }

    @Override public void apply(RequestTemplate template) {
      template.header("User-Agent", "Feign");
    }
  }

  @Test
  public void multipleInterceptor() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api = Feign.create(TestInterface.class, "http://localhost:" + server.getPort(),
        new TestInterface.Module(), new ForwardedForInterceptor(), new UserAgentInterceptor());

    api.post();
    RecordedRequest request = server.takeRequest();
    assertEquals("origin.host.com", request.getHeader("X-Forwarded-For"));
    assertEquals("Feign", request.getHeader("User-Agent"));
  }

  @Test public void toKeyMethodFormatsAsExpected() throws Exception {
    assertEquals("TestInterface#post()", Feign.configKey(TestInterface.class.getDeclaredMethod("post")));
    assertEquals("TestInterface#uriParam(String,URI,String)", Feign.configKey(
        TestInterface.class.getDeclaredMethod("uriParam", String.class, URI.class, String.class)));
  }

  @dagger.Module(overrides = true, library = true, includes = TestInterface.Module.class)
  static class IllegalArgumentExceptionOn404 {
    @Provides @Singleton ErrorDecoder errorDecoder() {
      return new ErrorDecoder.Default() {

        @Override
        public Exception decode(String methodKey, Response response) {
          if (response.status() == 404)
            return new IllegalArgumentException("zone not found");
          return super.decode(methodKey, response);
        }

      };
    }
  }

  @Test
  public void canOverrideErrorDecoder() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setResponseCode(404).setBody("foo"));
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("zone not found");

    TestInterface api = Feign.create(TestInterface.class, "http://localhost:" + server.getPort(),
          new IllegalArgumentExceptionOn404());

    api.post();
  }

  @Test public void retriesLostConnectionBeforeRead() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
    server.enqueue(new MockResponse().setBody("success!"));

    TestInterface api = Feign.create(TestInterface.class, "http://localhost:" + server.getPort(),
        new TestInterface.Module());

    api.post();
    assertEquals(2, server.getRequestCount());
  }

  @dagger.Module(overrides = true, library = true, includes = TestInterface.Module.class)
  static class DecodeFail {
    @Provides Decoder decoder() {
      return new Decoder() {
        @Override
        public Object decode(Response response, Type type) {
          return "fail";
        }
      };
    }
  }

  public void overrideTypeSpecificDecoder() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("success!"));

    TestInterface api = Feign.create(TestInterface.class, "http://localhost:" + server.getPort(),
        new DecodeFail());

    assertEquals(api.post(), "fail");
    assertEquals(1, server.getRequestCount());
  }

  @dagger.Module(overrides = true, library = true, includes = TestInterface.Module.class)
  static class RetryableExceptionOnRetry {
    @Provides Decoder decoder() {
      return new StringDecoder() {
        @Override
        public Object decode(Response response, Type type) throws IOException, FeignException {
          String string = super.decode(response, type).toString();
          if ("retry!".equals(string))
            throw new RetryableException(string, null);
          return string;
        }
      };
    }
  }

  /**
   * when you must parse a 2xx status to determine if the operation succeeded or not.
   */
  public void retryableExceptionInDecoder() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("retry!"));
    server.enqueue(new MockResponse().setBody("success!"));

    TestInterface api = Feign.create(TestInterface.class, "http://localhost:" + server.getPort(),
        new RetryableExceptionOnRetry());

    assertEquals(api.post(), "success!");
    assertEquals(2, server.getRequestCount());
  }

  @dagger.Module(overrides = true, library = true, includes = TestInterface.Module.class)
  static class IOEOnDecode {
    @Provides Decoder decoder() {
      return new Decoder() {
        @Override
        public Object decode(Response response, Type type) throws IOException {
          throw new IOException("error reading response");
        }
      };
    }
  }

  @Test
  public void doesntRetryAfterResponseIsSent() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("success!"));
    thrown.expect(FeignException.class);
    thrown.expectMessage("error reading response POST http://");

    TestInterface api = Feign.create(TestInterface.class, "http://localhost:" + server.getPort(),
        new IOEOnDecode());

    try {
      api.post();
    } finally {
      assertEquals(1, server.getRequestCount());
    }
  }

  @Module(overrides = true, includes = TestInterface.Module.class)
  static class TrustSSLSockets {
    @Provides SSLSocketFactory trustingSSLSocketFactory() {
      return TrustingSSLSocketFactory.get();
    }
  }

  @Test public void canOverrideSSLSocketFactory() throws IOException, InterruptedException {
    MockWebServer server = new MockWebServer();
    server.useHttps(TrustingSSLSocketFactory.get("localhost"), false);
    server.enqueue(new MockResponse().setBody("success!"));
    server.play();

    try {
      TestInterface api = Feign.create(TestInterface.class, "https://localhost:" + server.getPort(),
          new TrustSSLSockets());
      api.post();
    } finally {
      server.shutdown();
    }
  }

  @Module(overrides = true, includes = TrustSSLSockets.class)
  static class DisableHostnameVerification {
    @Provides HostnameVerifier acceptAllHostnameVerifier() {
      return new AcceptAllHostnameVerifier();
    }
  }

  @Test public void canOverrideHostnameVerifier() throws IOException, InterruptedException {
    MockWebServer server = new MockWebServer();
    server.useHttps(TrustingSSLSocketFactory.get("bad.example.com"), false);
    server.enqueue(new MockResponse().setBody("success!"));
    server.play();

    try {
      TestInterface api = Feign.create(TestInterface.class, "https://localhost:" + server.getPort(),
          new DisableHostnameVerification());
      api.post();
    } finally {
      server.shutdown();
    }
  }

  @Test public void retriesFailedHandshake() throws IOException, InterruptedException {
    MockWebServer server = new MockWebServer();
    server.useHttps(TrustingSSLSocketFactory.get("localhost"), false);
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.FAIL_HANDSHAKE));
    server.enqueue(new MockResponse().setBody("success!"));
    server.play();

    try {
      TestInterface api = Feign.create(TestInterface.class, "https://localhost:" + server.getPort(),
          new TestInterface.Module(), new TrustSSLSockets());
      api.post();
      assertEquals(2, server.getRequestCount());
    } finally {
      server.shutdown();
    }
  }

  @Test public void equalsHashCodeAndToStringWork() {
    Target<TestInterface> t1 = new HardCodedTarget<TestInterface>(TestInterface.class, "http://localhost:8080");
    Target<TestInterface> t2 = new HardCodedTarget<TestInterface>(TestInterface.class, "http://localhost:8888");
    Target<OtherTestInterface> t3 =
        new HardCodedTarget<OtherTestInterface>(OtherTestInterface.class, "http://localhost:8080");
    TestInterface i1 = Feign.builder().target(t1);
    TestInterface i2 = Feign.builder().target(t1);
    TestInterface i3 = Feign.builder().target(t2);
    OtherTestInterface i4 = Feign.builder().target(t3);

    assertEquals(i1, i1);
    assertEquals(i2, i1);
    assertNotEquals(i3, i1);
    assertNotEquals(i4, i1);

    assertEquals(i1.hashCode(), i1.hashCode());
    assertEquals(i2.hashCode(), i1.hashCode());
    assertNotEquals(i3.hashCode(), i1.hashCode());
    assertNotEquals(i4.hashCode(), i1.hashCode());

    assertEquals(t1.hashCode(), i1.hashCode());
    assertEquals(t2.hashCode(), i3.hashCode());
    assertEquals(t3.hashCode(), i4.hashCode());

    assertEquals(i1.toString(), i1.toString());
    assertEquals(i2.toString(), i1.toString());
    assertNotEquals(i3.toString(), i1.toString());
    assertNotEquals(i4.toString(), i1.toString());

    assertEquals(t1.toString(), i1.toString());
    assertEquals(t2.toString(), i3.toString());
    assertEquals(t3.toString(), i4.toString());
  }

  @Test public void decodeLogicSupportsByteArray() throws Exception {
    byte[] expectedResponse = {12, 34, 56};
    server.enqueue(new MockResponse().setBody(expectedResponse));

    OtherTestInterface api = Feign.builder().target(OtherTestInterface.class, "http://localhost:" + server.getPort());

    byte[] actualResponse = api.binaryResponseBody();
    assertArrayEquals(expectedResponse, actualResponse);
  }

  @Test public void encodeLogicSupportsByteArray() throws Exception {
    byte[] expectedRequest = {12, 34, 56};
    server.enqueue(new MockResponse());

    OtherTestInterface api = Feign.builder().target(OtherTestInterface.class, "http://localhost:" + server.getPort());

    api.binaryRequestBody(expectedRequest);
    byte[] actualRequest = server.takeRequest().getBody();
    assertArrayEquals(expectedRequest, actualRequest);
  }
}
