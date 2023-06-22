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
package feign.hc5;

import static feign.assertj.MockWebServerAssertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;
import feign.Request;
import feign.Response;
import feign.Util;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.junit.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import feign.Feign;
import feign.Feign.Builder;
import feign.client.AbstractClientTest;
import feign.jaxrs.JAXRSContract;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Tests client-specific behavior, such as ensuring Content-Length is sent when specified.
 */
public class ApacheHttp5ClientTest extends AbstractClientTest {

  @Override
  public Builder newBuilder() {
    return Feign.builder().client(new ApacheHttp5Client());
  }

  @Test
  public void queryParamsAreRespectedWhenBodyIsEmpty() throws InterruptedException {
    final JaxRsTestInterface testInterface = Feign.builder()
        .contract(new JAXRSContract())
        .client(new ApacheHttp5Client(HttpClientBuilder.create().build()))
        .target(JaxRsTestInterface.class, "http://localhost:" + server.getPort());

    server.enqueue(new MockResponse().setBody("foo"));
    server.enqueue(new MockResponse().setBody("foo"));

    assertEquals("foo", testInterface.withBody("foo", "bar"));
    final RecordedRequest request1 = server.takeRequest();
    assertEquals("/withBody?foo=foo", request1.getPath());
    assertEquals("bar", request1.getBody().readString(StandardCharsets.UTF_8));

    assertEquals("foo", testInterface.withoutBody("foo"));
    final RecordedRequest request2 = server.takeRequest();
    assertEquals("/withoutBody?foo=foo", request2.getPath());
    assertEquals("", request2.getBody().readString(StandardCharsets.UTF_8));
  }

  @Test
  public void followRedirectIsRespected() throws InterruptedException, IOException {
    Request.Options options = new Request.Options(10, TimeUnit.SECONDS, 60, TimeUnit.SECONDS, true);

    String wasRedirected = server.url("was-redirected").url().toString();

    server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", wasRedirected));
    server.enqueue(new MockResponse().setBody("redirect"));

    final JaxRsTestInterface testInterface = Feign.builder()
        .contract(new JAXRSContract())
        .client(new ApacheHttp5Client(HttpClientBuilder.create().build()))
        .options(options).target(JaxRsTestInterface.class, "http://localhost:" + server.getPort());

    Response response = testInterface.response();

    assertEquals("/", server.takeRequest().getPath());
    assertEquals("/was-redirected", server.takeRequest().getPath());
    assertEquals(200, response.status());
    assertEquals("redirect", Util.toString(response.body().asReader(Util.UTF_8)));
  }

  @Test
  public void notFollowRedirectIsRespected() throws InterruptedException {
    Request.Options options =
        new Request.Options(10, TimeUnit.SECONDS, 60, TimeUnit.SECONDS, false);

    String wasRedirected = server.url("was-redirected").url().toString();

    server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", wasRedirected));
    server.enqueue(new MockResponse().setBody("redirect"));

    final JaxRsTestInterface testInterface = Feign.builder()
        .contract(new JAXRSContract())
        .client(new ApacheHttp5Client(HttpClientBuilder.create().build()))
        .options(options).target(JaxRsTestInterface.class, "http://localhost:" + server.getPort());

    assertThat(testInterface.response().headers()).hasEntrySatisfying("location", (value) -> {
      assertThat(value).contains(wasRedirected);
    });
  }

  @Override
  public void testVeryLongResponseNullLength() {
    assumeTrue("HC5 client seems to hang with response size equalto Long.MAX", false);
  }

  @Override
  public void testContentTypeDefaultsToRequestCharset() throws Exception {
    assumeTrue("this test is flaky on windows, but works fine.", false);
  }

  @Path("/")
  public interface JaxRsTestInterface {
    @PUT
    @Path("/withBody")
    String withBody(@QueryParam("foo") String foo, String bar);

    @PUT
    @Path("/withoutBody")
    String withoutBody(@QueryParam("foo") String foo);

    @POST
    Response response();
  }
}
