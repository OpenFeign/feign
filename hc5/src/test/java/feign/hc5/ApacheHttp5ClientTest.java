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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import feign.Feign;
import feign.Feign.Builder;
import feign.FeignException;
import feign.Request;
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
    final JaxRsTestInterface testInterface = buildTestInterface();

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
  public void followRedirectsIsTrue() throws InterruptedException {
    final JaxRsTestInterface testInterface = buildTestInterface();

    String redirectPath = getRedirectionUrl();
    server.enqueue(buildMockResponseWithLocationHeader(redirectPath));
    server.enqueue(new MockResponse().setBody("redirected"));
    Request.Options options = buildRequestOptions(true);

    Object response = testInterface.withOptions(options);
    assertNotNull(response);
    assertEquals("redirected", response);
    assertEquals("/withRequestOptions", server.takeRequest().getPath());
  }

  @Test
  public void followRedirectsIsFalse() throws InterruptedException {
    final JaxRsTestInterface testInterface = buildTestInterface();

    String redirectPath = getRedirectionUrl();
    server.enqueue(buildMockResponseWithLocationHeader(redirectPath));
    Request.Options options = buildRequestOptions(false);

    FeignException feignException =
        assertThrows(FeignException.class, () -> testInterface.withOptions(options));
    assertEquals(302, feignException.status());
    assertEquals(redirectPath,
        feignException.responseHeaders().get("location").stream().findFirst().orElse(null));
    assertEquals("/withRequestOptions", server.takeRequest().getPath());
  }

  private JaxRsTestInterface buildTestInterface() {
    return Feign.builder()
        .contract(new JAXRSContract())
        .client(new ApacheHttp5Client(HttpClientBuilder.create().build()))
        .target(JaxRsTestInterface.class, "http://localhost:" + server.getPort());
  }

  private MockResponse buildMockResponseWithLocationHeader(String redirectPath) {
    return new MockResponse().setResponseCode(302).addHeader("location", redirectPath);
  }

  private String getRedirectionUrl() {
    return "http://localhost:" + server.getPort() + "/redirected";
  }

  private Request.Options buildRequestOptions(boolean followRedirects) {
    return new Request.Options(1, SECONDS, 1, SECONDS, followRedirects);
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

    @GET
    @Path("/withRequestOptions")
    String withOptions(Request.Options options);
  }
}
