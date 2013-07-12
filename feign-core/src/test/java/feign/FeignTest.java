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

import static org.testng.Assert.assertEquals;

import com.google.common.collect.ImmutableMap;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.SocketPolicy;
import dagger.Module;
import dagger.Provides;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import feign.codec.StringDecoder;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.Map;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.net.ssl.SSLSocketFactory;
import org.testng.annotations.Test;

@Test
public class FeignTest {
  interface TestInterface {
    @RequestLine("POST /")
    String post();

    @RequestLine("POST /")
    @Body(
        "%7B\"customer_name\": \"{customer_name}\", \"user_name\": \"{user_name}\", \"password\":"
            + " \"{password}\"%7D")
    void login(
        @Named("customer_name") String customer,
        @Named("user_name") String user,
        @Named("password") String password);

    @RequestLine("GET /{1}/{2}")
    Response uriParam(@Named("1") String one, URI endpoint, @Named("2") String two);

    @dagger.Module(overrides = true, library = true)
    static class Module {
      // until dagger supports real map binding, we need to recreate the
      // entire map, as opposed to overriding a single entry.
      @Provides
      @Singleton
      Map<String, Decoder> decoders() {
        return ImmutableMap.<String, Decoder>of("TestInterface", new StringDecoder());
      }
    }
  }

  @Test
  public void postTemplateParamsResolve() throws IOException, InterruptedException {
    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setResponseCode(200).setBody("foo"));
    server.play();

    try {
      TestInterface api =
          Feign.create(
              TestInterface.class, server.getUrl("").toString(), new TestInterface.Module());

      api.login("netflix", "denominator", "password");
      assertEquals(
          new String(server.takeRequest().getBody()),
          "{\"customer_name\": \"netflix\", \"user_name\": \"denominator\", \"password\":"
              + " \"password\"}");
    } finally {
      server.shutdown();
    }
  }

  @Test
  public void toKeyMethodFormatsAsExpected() throws Exception {
    assertEquals(
        Feign.configKey(TestInterface.class.getDeclaredMethod("post")), "TestInterface#post()");
    assertEquals(
        Feign.configKey(
            TestInterface.class.getDeclaredMethod(
                "uriParam", String.class, URI.class, String.class)),
        "TestInterface#uriParam(String,URI,String)");
  }

  @Test(
      expectedExceptions = IllegalArgumentException.class,
      expectedExceptionsMessageRegExp = "zone not found")
  public void canOverrideErrorDecoderOnMethod() throws IOException, InterruptedException {
    @dagger.Module(overrides = true, includes = TestInterface.Module.class)
    class Overrides {
      @Provides
      @Singleton
      Map<String, ErrorDecoder> decoders() {
        return ImmutableMap.<String, ErrorDecoder>of(
            "TestInterface#post()",
            new ErrorDecoder() {

              @Override
              public Exception decode(String methodKey, Response response) {
                if (response.status() == 404) return new IllegalArgumentException("zone not found");
                return ErrorDecoder.DEFAULT.decode(methodKey, response);
              }
            });
      }
    }

    final MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setResponseCode(404).setBody("foo"));
    server.play();

    try {
      TestInterface api =
          Feign.create(TestInterface.class, server.getUrl("").toString(), new Overrides());

      api.post();
    } finally {
      server.shutdown();
    }
  }

  @Test
  public void retriesLostConnectionBeforeRead() throws IOException, InterruptedException {
    MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("success!".getBytes()));
    server.play();

    try {
      TestInterface api =
          Feign.create(
              TestInterface.class, server.getUrl("").toString(), new TestInterface.Module());

      api.post();
      assertEquals(server.getRequestCount(), 2);

    } finally {
      server.shutdown();
    }
  }

  @Test(
      expectedExceptions = FeignException.class,
      expectedExceptionsMessageRegExp = "error reading response POST http://.*")
  public void doesntRetryAfterResponseIsSent() throws IOException, InterruptedException {
    MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setResponseCode(200).setBody("success!".getBytes()));
    server.play();

    try {
      @dagger.Module(overrides = true)
      class Overrides {
        @Provides
        @Singleton
        Map<String, Decoder> decoders() {
          return ImmutableMap.<String, Decoder>of(
              "TestInterface",
              new Decoder() {

                @Override
                public Object decode(String methodKey, Reader reader, Type type)
                    throws IOException {
                  throw new IOException("error reading response");
                }
              });
        }
      }
      TestInterface api =
          Feign.create(TestInterface.class, server.getUrl("").toString(), new Overrides());

      api.post();
    } finally {
      server.shutdown();
      assertEquals(server.getRequestCount(), 1);
    }
  }

  @Module(injects = Client.Default.class, overrides = true)
  static class TrustSSLSockets {
    @Provides
    SSLSocketFactory trustingSSLSocketFactory() {
      return TrustingSSLSocketFactory.get();
    }
  }

  @Test
  public void canOverrideSSLSocketFactory() throws IOException, InterruptedException {
    MockWebServer server = new MockWebServer();
    server.useHttps(TrustingSSLSocketFactory.get(), false);
    server.enqueue(new MockResponse().setResponseCode(200).setBody("success!".getBytes()));
    server.play();

    try {
      TestInterface api =
          Feign.create(
              TestInterface.class,
              server.getUrl("").toString(),
              new TestInterface.Module(),
              new TrustSSLSockets());
      api.post();
    } finally {
      server.shutdown();
    }
  }

  @Test
  public void retriesFailedHandshake() throws IOException, InterruptedException {
    MockWebServer server = new MockWebServer();
    server.useHttps(TrustingSSLSocketFactory.get(), false);
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.FAIL_HANDSHAKE));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("success!".getBytes()));
    server.play();

    try {
      TestInterface api =
          Feign.create(
              TestInterface.class,
              server.getUrl("").toString(),
              new TestInterface.Module(),
              new TrustSSLSockets());
      api.post();
      assertEquals(server.getRequestCount(), 2);
    } finally {
      server.shutdown();
    }
  }
}
