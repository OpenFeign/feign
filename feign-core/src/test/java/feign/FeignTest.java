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
import com.google.mockwebserver.SocketPolicy;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.codec.StringDecoder;
import org.testng.annotations.Test;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static dagger.Provides.Type.SET;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@Test
// unbound wildcards are not currently injectable in dagger.
@SuppressWarnings("rawtypes")
public class FeignTest {

  @Test public void closeShutsdownExecutorService() throws IOException, InterruptedException {
    final ExecutorService service = Executors.newCachedThreadPool();
    new Feign(new Lazy<Executor>() {
      @Override public Executor get() {
        return service;
      }
    }) {
      @Override public <T> T newInstance(Target<T> target) {
        return null;
      }
    }.close();
    assertTrue(service.isShutdown());
  }

  interface TestInterface {
    @RequestLine("POST /") String post();

    @RequestLine("POST /")
    @Body("%7B\"customer_name\": \"{customer_name}\", \"user_name\": \"{user_name}\", \"password\": \"{password}\"%7D")
    void login(
        @Named("customer_name") String customer, @Named("user_name") String user, @Named("password") String password);

    @RequestLine("POST /") void body(List<String> contents);

    @RequestLine("POST /") void form(
        @Named("customer_name") String customer, @Named("user_name") String user, @Named("password") String password);

    @RequestLine("GET /{1}/{2}") Response uriParam(@Named("1") String one, URI endpoint, @Named("2") String two);

    @RequestLine("POST /") Observable<Void> observableVoid();

    @RequestLine("POST /") Observable<String> observableString();

    @RequestLine("POST /") Observable<Response> observableResponse();

    @dagger.Module(overrides = true, library = true)
    static class Module {
      @Provides(type = SET) Encoder defaultEncoder() {
        return new Encoder.Text<Object>() {
          @Override public String encode(Object object) {
            return object.toString();
          }
        };
      }

      @Provides(type = SET) Encoder formEncoder() {
        return new Encoder.Text<Map<String, ?>>() {
          @Override public String encode(Map<String, ?> object) {
            return Joiner.on(',').withKeyValueSeparator("=").join(object);
          }
        };
      }

      // just run synchronously
      @Provides @Singleton @Named("http") Executor httpExecutor() {
        return new Executor() {
          @Override public void execute(Runnable command) {
            command.run();
          }
        };
      }
    }
  }

  @Test
  public void observableVoid() throws IOException, InterruptedException {
    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setResponseCode(200).setBody("foo"));
    server.play();

    try {
      TestInterface api = Feign.create(TestInterface.class, "http://localhost:" + server.getPort(), new TestInterface.Module());

      final AtomicBoolean success = new AtomicBoolean();

      Observer<Void> observer = new Observer<Void>() {

        @Override public void onNext(Void element) {
          fail("on next isn't valid for void");
        }

        @Override public void onSuccess() {
          success.set(true);
        }

        @Override public void onFailure(Throwable cause) {
          fail(cause.getMessage());
        }
      };
      api.observableVoid().subscribe(observer);

      assertTrue(success.get());
      assertEquals(server.getRequestCount(), 1);
    } finally {
      server.shutdown();
    }
  }

  @Test
  public void observableResponse() throws IOException, InterruptedException {
    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setResponseCode(200).setBody("foo"));
    server.play();

    try {
      TestInterface api = Feign.create(TestInterface.class, "http://localhost:" + server.getPort(), new TestInterface.Module());

      final AtomicBoolean success = new AtomicBoolean();

      Observer<Response> observer = new Observer<Response>() {

        @Override public void onNext(Response element) {
          assertEquals(element.status(), 200);
        }

        @Override public void onSuccess() {
          success.set(true);
        }

        @Override public void onFailure(Throwable cause) {
          fail(cause.getMessage());
        }
      };
      api.observableResponse().subscribe(observer);

      assertTrue(success.get());
      assertEquals(server.getRequestCount(), 1);
    } finally {
      server.shutdown();
    }
  }

  @Test
  public void incrementString() throws IOException, InterruptedException {
    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setResponseCode(200).setBody("foo"));
    server.play();

    try {
      TestInterface api = Feign.create(TestInterface.class, "http://localhost:" + server.getPort(), new TestInterface.Module());

      final AtomicBoolean success = new AtomicBoolean();

      Observer<String> observer = new Observer<String>() {

        @Override public void onNext(String element) {
          assertEquals(element, "foo");
        }

        @Override public void onSuccess() {
          success.set(true);
        }

        @Override public void onFailure(Throwable cause) {
          fail(cause.getMessage());
        }
      };
      api.observableString().subscribe(observer);

      assertTrue(success.get());
      assertEquals(server.getRequestCount(), 1);
    } finally {
      server.shutdown();
    }
  }

  @Test
  public void multipleObservers() throws IOException, InterruptedException {
    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setResponseCode(200).setBody("foo"));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("foo"));
    server.play();

    try {
      TestInterface api = Feign.create(TestInterface.class, "http://localhost:" + server.getPort(), new TestInterface.Module());

      final CountDownLatch latch = new CountDownLatch(2);

      Observer<String> observer = new Observer<String>() {

        @Override public void onNext(String element) {
          assertEquals(element, "foo");
        }

        @Override public void onSuccess() {
          latch.countDown();
        }

        @Override public void onFailure(Throwable cause) {
          fail(cause.getMessage());
        }
      };

      Observable<String> observable = api.observableString();
      observable.subscribe(observer);
      observable.subscribe(observer);
      latch.await();

      assertEquals(server.getRequestCount(), 2);
    } finally {
      server.shutdown();
    }
  }

  @Test
  public void postTemplateParamsResolve() throws IOException, InterruptedException {
    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setResponseCode(200).setBody("foo"));
    server.play();

    try {
      TestInterface api = Feign.create(TestInterface.class, "http://localhost:" + server.getPort(), new TestInterface.Module());

      api.login("netflix", "denominator", "password");
      assertEquals(new String(server.takeRequest().getBody()),
          "{\"customer_name\": \"netflix\", \"user_name\": \"denominator\", \"password\": \"password\"}");
    } finally {
      server.shutdown();
    }
  }

  @Test
  public void postFormParams() throws IOException, InterruptedException {
    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setResponseCode(200).setBody("foo"));
    server.play();

    try {
      TestInterface api = Feign.create(TestInterface.class, "http://localhost:" + server.getPort(), new TestInterface.Module());

      api.form("netflix", "denominator", "password");
      assertEquals(new String(server.takeRequest().getBody()),
          "customer_name=netflix,user_name=denominator,password=password");
    } finally {
      server.shutdown();
    }
  }

  @Test
  public void postBodyParam() throws IOException, InterruptedException {
    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setResponseCode(200).setBody("foo"));
    server.play();

    try {
      TestInterface api = Feign.create(TestInterface.class, "http://localhost:" + server.getPort(), new TestInterface.Module());

      api.body(Arrays.asList("netflix", "denominator", "password"));
      assertEquals(new String(server.takeRequest().getBody()), "[netflix, denominator, password]");
    } finally {
      server.shutdown();
    }
  }

  @Test public void toKeyMethodFormatsAsExpected() throws Exception {
    assertEquals(Feign.configKey(TestInterface.class.getDeclaredMethod("post")), "TestInterface#post()");
    assertEquals(Feign.configKey(TestInterface.class.getDeclaredMethod("uriParam", String.class, URI.class,
        String.class)), "TestInterface#uriParam(String,URI,String)");
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

  @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "zone not found")
  public void canOverrideErrorDecoder() throws IOException, InterruptedException {

    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setResponseCode(404).setBody("foo"));
    server.play();

    try {
      TestInterface api = Feign.create(TestInterface.class, "http://localhost:" + server.getPort(),
          new IllegalArgumentExceptionOn404());

      api.post();
    } finally {
      server.shutdown();
    }
  }

  @Test public void retriesLostConnectionBeforeRead() throws IOException, InterruptedException {
    MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("success!".getBytes()));
    server.play();

    try {
      TestInterface api = Feign.create(TestInterface.class, "http://localhost:" + server.getPort(),
          new TestInterface.Module());

      api.post();
      assertEquals(server.getRequestCount(), 2);

    } finally {
      server.shutdown();
    }
  }

  @dagger.Module(overrides = true, library = true, includes = TestInterface.Module.class)
  static class DecodeFail {
    @Provides(type = SET) Decoder decoder() {
      return new Decoder.TextStream<String>() {
        @Override
        public String decode(Reader reader, Type type) throws IOException {
          return "fail";
        }
      };
    }
  }

  public void overrideTypeSpecificDecoder() throws IOException, InterruptedException {
    MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setResponseCode(200).setBody("success!".getBytes()));
    server.play();

    try {
      TestInterface api = Feign.create(TestInterface.class, "http://localhost:" + server.getPort(),
          new DecodeFail());

      assertEquals(api.post(), "fail");
    } finally {
      server.shutdown();
      assertEquals(server.getRequestCount(), 1);
    }
  }

  @dagger.Module(overrides = true, library = true, includes = TestInterface.Module.class)
  static class RetryableExceptionOnRetry {
    @Provides(type = SET) Decoder decoder() {
      return new StringDecoder() {
        @Override
        public String decode(Reader reader, Type type) throws RetryableException, IOException {
          String string = super.decode(reader, type);
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
    MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setResponseCode(200).setBody("retry!".getBytes()));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("success!".getBytes()));
    server.play();

    try {
      TestInterface api = Feign.create(TestInterface.class, "http://localhost:" + server.getPort(),
          new RetryableExceptionOnRetry());

      assertEquals(api.post(), "success!");
    } finally {
      server.shutdown();
      assertEquals(server.getRequestCount(), 2);
    }
  }

  @dagger.Module(overrides = true, library = true, includes = TestInterface.Module.class)
  static class IOEOnDecode {
    @Provides(type = SET) Decoder decoder() {
      return new Decoder.TextStream<String>() {
        @Override
        public String decode(Reader reader, Type type) throws IOException {
          throw new IOException("error reading response");
        }
      };
    }
  }

  @Test(expectedExceptions = FeignException.class, expectedExceptionsMessageRegExp = "error reading response POST http://.*")
  public void doesntRetryAfterResponseIsSent() throws IOException, InterruptedException {
    MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setResponseCode(200).setBody("success!".getBytes()));
    server.play();

    try {
      TestInterface api = Feign.create(TestInterface.class, "http://localhost:" + server.getPort(),
          new IOEOnDecode());

      api.post();
    } finally {
      server.shutdown();
      assertEquals(server.getRequestCount(), 1);
    }
  }

  @Module(injects = Client.Default.class, overrides = true)
  static class TrustSSLSockets {
    @Provides SSLSocketFactory trustingSSLSocketFactory() {
      return TrustingSSLSocketFactory.get();
    }
  }

  @Test public void canOverrideSSLSocketFactory() throws IOException, InterruptedException {
    MockWebServer server = new MockWebServer();
    server.useHttps(TrustingSSLSocketFactory.get(), false);
    server.enqueue(new MockResponse().setResponseCode(200).setBody("success!".getBytes()));
    server.play();

    try {
      TestInterface api = Feign.create(TestInterface.class, "https://localhost:" + server.getPort(),
          new TestInterface.Module(), new TrustSSLSockets());
      api.post();
    } finally {
      server.shutdown();
    }
  }

  @Test public void retriesFailedHandshake() throws IOException, InterruptedException {
    MockWebServer server = new MockWebServer();
    server.useHttps(TrustingSSLSocketFactory.get(), false);
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.FAIL_HANDSHAKE));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("success!".getBytes()));
    server.play();

    try {
      TestInterface api = Feign.create(TestInterface.class, "https://localhost:" + server.getPort(),
          new TestInterface.Module(), new TrustSSLSockets());
      api.post();
      assertEquals(server.getRequestCount(), 2);
    } finally {
      server.shutdown();
    }
  }
}
