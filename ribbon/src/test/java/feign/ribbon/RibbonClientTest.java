/**
 * Copyright 2012-2018 The Feign Authors
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
package feign.ribbon;

import static com.netflix.config.ConfigurationManager.getConfigInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.SocketPolicy;
import okhttp3.mockwebserver.MockWebServer;
import feign.Client;
import feign.Feign;
import feign.Param;
import feign.Request;
import feign.Response;
import feign.RequestLine;
import feign.RetryableException;
import feign.Retryer;
import feign.client.TrustingSSLSocketFactory;

public class RibbonClientTest {

  @Rule
  public final TestName testName = new TestName();
  @Rule
  public final MockWebServer server1 = new MockWebServer();
  @Rule
  public final MockWebServer server2 = new MockWebServer();

  private static String oldRetryConfig = null;

  private static final String SUN_RETRY_PROPERTY = "sun.net.http.retryPost";

  @BeforeClass
  public static void disableSunRetry() throws Exception {
    // The Sun HTTP Client retries all requests once on an IOException, which makes testing retry
    // code harder than would
    // be ideal. We can only disable it for post, so lets at least do that.
    oldRetryConfig = System.setProperty(SUN_RETRY_PROPERTY, "false");
  }

  @AfterClass
  public static void resetSunRetry() throws Exception {
    if (oldRetryConfig == null) {
      System.clearProperty(SUN_RETRY_PROPERTY);
    } else {
      System.setProperty(SUN_RETRY_PROPERTY, oldRetryConfig);
    }
  }

  static String hostAndPort(URL url) {
    // our build slaves have underscores in their hostnames which aren't permitted by ribbon
    return "localhost:" + url.getPort();
  }

  @Test
  public void loadBalancingDefaultPolicyRoundRobin() throws IOException, InterruptedException {
    server1.enqueue(new MockResponse().setBody("success!"));
    server2.enqueue(new MockResponse().setBody("success!"));

    getConfigInstance().setProperty(serverListKey(),
        hostAndPort(server1.url("").url()) + "," + hostAndPort(
            server2.url("").url()));

    TestInterface api = Feign.builder().client(RibbonClient.create())
        .target(TestInterface.class, "http://" + client());

    api.post();
    api.post();

    assertEquals(1, server1.getRequestCount());
    assertEquals(1, server2.getRequestCount());
    // TODO: verify ribbon stats match
    // assertEquals(target.lb().getLoadBalancerStats().getSingleServerStat())
  }

  @Test
  public void ioExceptionRetry() throws IOException, InterruptedException {
    server1.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
    server1.enqueue(new MockResponse().setBody("success!"));

    getConfigInstance().setProperty(serverListKey(), hostAndPort(server1.url("").url()));

    TestInterface api = Feign.builder().client(RibbonClient.create())
        .target(TestInterface.class, "http://" + client());

    api.post();

    assertEquals(2, server1.getRequestCount());
    // TODO: verify ribbon stats match
    // assertEquals(target.lb().getLoadBalancerStats().getSingleServerStat())
  }

  @Test
  public void ioExceptionFailsAfterTooManyFailures() throws IOException, InterruptedException {
    server1.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

    getConfigInstance().setProperty(serverListKey(), hostAndPort(server1.url("").url()));

    TestInterface api =
        Feign.builder().client(RibbonClient.create()).retryer(Retryer.NEVER_RETRY)
            .target(TestInterface.class, "http://" + client());

    try {
      api.post();
      fail("No exception thrown");
    } catch (RetryableException ignored) {

    }
    // TODO: why are these retrying?
    assertThat(server1.getRequestCount()).isGreaterThanOrEqualTo(1);
    // TODO: verify ribbon stats match
    // assertEquals(target.lb().getLoadBalancerStats().getSingleServerStat())
  }

  @Test
  public void ribbonRetryConfigurationOnSameServer() throws IOException, InterruptedException {
    server1.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
    server1.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
    server2.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
    server2.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

    getConfigInstance().setProperty(serverListKey(),
        hostAndPort(server1.url("").url()) + "," + hostAndPort(server2.url("").url()));
    getConfigInstance().setProperty(client() + ".ribbon.MaxAutoRetries", 1);

    TestInterface api = Feign.builder().client(RibbonClient.create()).retryer(Retryer.NEVER_RETRY)
        .target(TestInterface.class, "http://" + client());

    try {
      api.post();
      fail("No exception thrown");
    } catch (RetryableException ignored) {

    }
    assertTrue(server1.getRequestCount() >= 2 || server2.getRequestCount() >= 2);
    assertThat(server1.getRequestCount() + server2.getRequestCount()).isGreaterThanOrEqualTo(2);
    // TODO: verify ribbon stats match
    // assertEquals(target.lb().getLoadBalancerStats().getSingleServerStat())
  }

  @Test
  public void ribbonRetryConfigurationOnMultipleServers() throws IOException, InterruptedException {
    server1.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
    server1.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
    server2.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
    server2.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

    getConfigInstance().setProperty(serverListKey(),
        hostAndPort(server1.url("").url()) + "," + hostAndPort(server2.url("").url()));
    getConfigInstance().setProperty(client() + ".ribbon.MaxAutoRetriesNextServer", 1);

    TestInterface api = Feign.builder().client(RibbonClient.create()).retryer(Retryer.NEVER_RETRY)
        .target(TestInterface.class, "http://" + client());

    try {
      api.post();
      fail("No exception thrown");
    } catch (RetryableException ignored) {

    }
    assertThat(server1.getRequestCount()).isGreaterThanOrEqualTo(1);
    assertThat(server1.getRequestCount()).isGreaterThanOrEqualTo(1);
    // TODO: verify ribbon stats match
    // assertEquals(target.lb().getLoadBalancerStats().getSingleServerStat())
  }

  /*
   * This test-case replicates a bug that occurs when using RibbonRequest with a query string.
   * 
   * The querystrings would not be URL-encoded, leading to invalid HTTP-requests if the query string
   * contained invalid characters (ex. space).
   */
  @Test
  public void urlEncodeQueryStringParameters() throws IOException, InterruptedException {
    String queryStringValue = "some string with space";

    /* values must be pct encoded, see RFC 6750 */
    String expectedQueryStringValue = "some%20string%20with%20space";
    String expectedRequestLine = String.format("GET /?a=%s HTTP/1.1", expectedQueryStringValue);

    server1.enqueue(new MockResponse().setBody("success!"));

    getConfigInstance().setProperty(serverListKey(), hostAndPort(server1.url("").url()));

    TestInterface api = Feign.builder().client(RibbonClient.create())
        .target(TestInterface.class, "http://" + client());

    api.getWithQueryParameters(queryStringValue);

    final String recordedRequestLine = server1.takeRequest().getRequestLine();

    assertEquals(recordedRequestLine, expectedRequestLine);
  }


  @Test
  public void testHTTPSViaRibbon() {

    Client trustSSLSockets = new Client.Default(TrustingSSLSocketFactory.get(), null);

    server1.useHttps(TrustingSSLSocketFactory.get("localhost"), false);
    server1.enqueue(new MockResponse().setBody("success!"));

    getConfigInstance().setProperty(serverListKey(), hostAndPort(server1.url("").url()));

    TestInterface api =
        Feign.builder().client(RibbonClient.builder().delegate(trustSSLSockets).build())
            .target(TestInterface.class, "https://" + client());
    api.post();
    assertEquals(1, server1.getRequestCount());

  }

  @Test
  public void ioExceptionRetryWithBuilder() throws IOException, InterruptedException {
    server1.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
    server1.enqueue(new MockResponse().setBody("success!"));

    getConfigInstance().setProperty(serverListKey(), hostAndPort(server1.url("").url()));

    TestInterface api =
        Feign.builder().client(RibbonClient.create())
            .target(TestInterface.class, "http://" + client());

    api.post();

    assertEquals(server1.getRequestCount(), 2);
    // TODO: verify ribbon stats match
    // assertEquals(target.lb().getLoadBalancerStats().getSingleServerStat())
  }

  @Test
  public void ribbonRetryOnStatusCodes() throws IOException, InterruptedException {
    server1.enqueue(new MockResponse().setResponseCode(502));
    server2.enqueue(new MockResponse().setResponseCode(503));

    getConfigInstance().setProperty(serverListKey(),
        hostAndPort(server1.url("").url()) + "," + hostAndPort(server2.url("").url()));
    getConfigInstance().setProperty(client() + ".ribbon.MaxAutoRetriesNextServer", 1);
    getConfigInstance().setProperty(client() + ".ribbon.RetryableStatusCodes", "503,502");

    TestInterface api =
        Feign.builder().client(RibbonClient.create()).retryer(Retryer.NEVER_RETRY)
            .target(TestInterface.class, "http://" + client());

    try {
      api.post();
      fail("No exception thrown");
    } catch (Exception ignored) {

    }
    assertEquals(1, server1.getRequestCount());
    assertEquals(1, server2.getRequestCount());
  }


  @Test
  public void testFeignOptionsFollowRedirect() {
    String expectedLocation = server2.url("").url().toString();
    server1
        .enqueue(new MockResponse().setResponseCode(302).setHeader("Location", expectedLocation));

    getConfigInstance().setProperty(serverListKey(), hostAndPort(server1.url("").url()));

    Request.Options options = new Request.Options(1000, 1000, false);
    TestInterface api = Feign.builder()
        .options(options)
        .client(RibbonClient.create())
        .retryer(Retryer.NEVER_RETRY)
        .target(TestInterface.class, "http://" + client());

    try {
      Response response = api.get();
      assertEquals(302, response.status());
      Collection<String> location = response.headers().get("Location");
      assertNotNull(location);
      assertFalse(location.isEmpty());
      assertEquals(expectedLocation, location.iterator().next());
    } catch (Exception ignored) {
      ignored.printStackTrace();
      fail("Shouldn't throw ");
    }

  }

  @Test
  public void testFeignOptionsNoFollowRedirect() {
    // 302 will say go to server 2
    server1.enqueue(new MockResponse().setResponseCode(302).setHeader("Location",
        server2.url("").url().toString()));
    // server 2 will send back 200 with "Hello" as body
    server2.enqueue(new MockResponse().setResponseCode(200).setBody("Hello"));

    getConfigInstance().setProperty(serverListKey(),
        hostAndPort(server1.url("").url()) + "," + hostAndPort(server2.url("").url()));

    Request.Options options = new Request.Options(1000, 1000, true);
    TestInterface api = Feign.builder()
        .options(options)
        .client(RibbonClient.create())
        .retryer(Retryer.NEVER_RETRY)
        .target(TestInterface.class, "http://" + client());

    try {
      Response response = api.get();
      assertEquals(200, response.status());
      assertEquals("Hello", response.body().toString());
    } catch (Exception ignored) {
      ignored.printStackTrace();
      fail("Shouldn't throw ");
    }

  }

  @Test
  public void testFeignOptionsClientConfig() {
    Request.Options options = new Request.Options(1111, 22222);
    IClientConfig config = new RibbonClient.FeignOptionsClientConfig(options);
    assertThat(config.get(CommonClientConfigKey.ConnectTimeout),
        equalTo(options.connectTimeoutMillis()));
    assertThat(config.get(CommonClientConfigKey.ReadTimeout), equalTo(options.readTimeoutMillis()));
    assertThat(config.get(CommonClientConfigKey.FollowRedirects),
        equalTo(options.isFollowRedirects()));
    assertEquals(3, config.getProperties().size());
  }

  @Test
  public void testCleanUrlWithMatchingHostAndPart() throws IOException {
    URI uri = RibbonClient.cleanUrl("http://questions/questions/answer/123", "questions");
    assertEquals("http:///questions/answer/123", uri.toString());
  }

  @Test
  public void testCleanUrl() throws IOException {
    URI uri = RibbonClient.cleanUrl("http://myservice/questions/answer/123", "myservice");
    assertEquals("http:///questions/answer/123", uri.toString());
  }

  private String client() {
    return testName.getMethodName();
  }

  private String serverListKey() {
    return client() + ".ribbon.listOfServers";
  }

  @After
  public void clearServerList() {
    getConfigInstance().clearProperty(serverListKey());
  }

  interface TestInterface {

    @RequestLine("POST /")
    void post();

    @RequestLine("GET /?a={a}")
    void getWithQueryParameters(@Param("a") String a);

    @RequestLine("GET /")
    Response get();
  }
}
