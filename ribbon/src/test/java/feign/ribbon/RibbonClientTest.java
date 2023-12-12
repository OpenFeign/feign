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
package feign.ribbon;

import static com.netflix.config.ConfigurationManager.getConfigInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import feign.Client;
import feign.Feign;
import feign.Param;
import feign.Request;
import feign.RequestLine;
import feign.Response;
import feign.RetryableException;
import feign.Retryer;
import feign.client.TrustingSSLSocketFactory;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.SocketPolicy;

@Disabled("inconsistent, deprecated toolset")
public class RibbonClientTest {


  public String testName;
  public final MockWebServer server1 = new MockWebServer();
  public final MockWebServer server2 = new MockWebServer();

  private static String oldRetryConfig = null;

  private static final String SUN_RETRY_PROPERTY = "sun.net.http.retryPost";

  @BeforeAll
  static void disableSunRetry() throws Exception {
    // The Sun HTTP Client retries all requests once on an IOException, which makes testing retry
    // code harder than would
    // be ideal. We can only disable it for post, so lets at least do that.
    oldRetryConfig = System.setProperty(SUN_RETRY_PROPERTY, "false");
  }

  @AfterAll
  static void resetSunRetry() throws Exception {
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
  void loadBalancingDefaultPolicyRoundRobin() throws IOException, InterruptedException {
    server1.enqueue(new MockResponse().setBody("success!"));
    server2.enqueue(new MockResponse().setBody("success!"));

    getConfigInstance().setProperty(serverListKey(),
        hostAndPort(server1.url("").url()) + "," + hostAndPort(
            server2.url("").url()));

    TestInterface api = Feign.builder().client(RibbonClient.create())
        .target(TestInterface.class, "http://" + client());

    api.post();
    api.post();

    assertThat(server1.getRequestCount()).isEqualTo(1);
    assertThat(server2.getRequestCount()).isEqualTo(1);
    // TODO: verify ribbon stats match
    // assertEquals(target.lb().getLoadBalancerStats().getSingleServerStat())
  }

  @Test
  void ioExceptionRetry() throws IOException, InterruptedException {
    server1.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
    server1.enqueue(new MockResponse().setBody("success!"));

    getConfigInstance().setProperty(serverListKey(), hostAndPort(server1.url("").url()));

    TestInterface api = Feign.builder().client(RibbonClient.create())
        .target(TestInterface.class, "http://" + client());

    api.post();

    assertThat(server1.getRequestCount()).isEqualTo(2);
    // TODO: verify ribbon stats match
    // assertEquals(target.lb().getLoadBalancerStats().getSingleServerStat())
  }

  @Test
  void ioExceptionFailsAfterTooManyFailures() throws IOException, InterruptedException {
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
  void ribbonRetryConfigurationOnSameServer() throws IOException, InterruptedException {
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
    assertThat(server1.getRequestCount() >= 2 || server2.getRequestCount() >= 2).isTrue();
    assertThat(server1.getRequestCount() + server2.getRequestCount()).isGreaterThanOrEqualTo(2);
    // TODO: verify ribbon stats match
    // assertEquals(target.lb().getLoadBalancerStats().getSingleServerStat())
  }

  @Test
  void ribbonRetryConfigurationOnMultipleServers() throws IOException, InterruptedException {
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
  void urlEncodeQueryStringParameters() throws IOException, InterruptedException {
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

    assertThat(expectedRequestLine).isEqualTo(recordedRequestLine);
  }


  @Test
  void hTTPSViaRibbon() {

    Client trustSSLSockets = new Client.Default(TrustingSSLSocketFactory.get(), null);

    server1.useHttps(TrustingSSLSocketFactory.get("localhost"));
    server1.enqueue(new MockResponse().setBody("success!"));

    getConfigInstance().setProperty(serverListKey(), hostAndPort(server1.url("").url()));

    TestInterface api =
        Feign.builder().client(RibbonClient.builder().delegate(trustSSLSockets).build())
            .target(TestInterface.class, "https://" + client());
    api.post();
    assertThat(server1.getRequestCount()).isEqualTo(1);

  }

  @Test
  void ioExceptionRetryWithBuilder() throws IOException, InterruptedException {
    server1.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
    server1.enqueue(new MockResponse().setBody("success!"));

    getConfigInstance().setProperty(serverListKey(), hostAndPort(server1.url("").url()));

    TestInterface api =
        Feign.builder().client(RibbonClient.create())
            .target(TestInterface.class, "http://" + client());

    api.post();

    assertThat(2).isEqualTo(server1.getRequestCount());
    // TODO: verify ribbon stats match
    // assertEquals(target.lb().getLoadBalancerStats().getSingleServerStat())
  }

  @Test
  void ribbonRetryOnStatusCodes() throws IOException, InterruptedException {
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
    assertThat(server1.getRequestCount()).isEqualTo(1);
    assertThat(server2.getRequestCount()).isEqualTo(1);
  }


  @Test
  void feignOptionsFollowRedirect() {
    String expectedLocation = server2.url("").url().toString();
    server1
        .enqueue(new MockResponse().setResponseCode(302).setHeader("Location", expectedLocation));

    getConfigInstance().setProperty(serverListKey(), hostAndPort(server1.url("").url()));

    Request.Options options =
        new Request.Options(1000, TimeUnit.MILLISECONDS, 1000, TimeUnit.MILLISECONDS, false);
    TestInterface api = Feign.builder()
        .options(options)
        .client(RibbonClient.create())
        .retryer(Retryer.NEVER_RETRY)
        .target(TestInterface.class, "http://" + client());

    try {
      Response response = api.get();
      assertThat(response.status()).isEqualTo(302);
      Collection<String> location = response.headers().get("Location");
      assertThat(location).isNotNull();
      assertThat(location).isNotEmpty();
      assertThat(location.iterator().next()).isEqualTo(expectedLocation);
    } catch (Exception ignored) {
      ignored.printStackTrace();
      fail("Shouldn't throw ");
    }

  }

  @Test
  void feignOptionsNoFollowRedirect() {
    // 302 will say go to server 2
    server1.enqueue(new MockResponse().setResponseCode(302).setHeader("Location",
        server2.url("").url().toString()));
    // server 2 will send back 200 with "Hello" as body
    server2.enqueue(new MockResponse().setResponseCode(200).setBody("Hello"));

    getConfigInstance().setProperty(serverListKey(),
        hostAndPort(server1.url("").url()) + "," + hostAndPort(server2.url("").url()));

    Request.Options options =
        new Request.Options(1000, TimeUnit.MILLISECONDS, 1000, TimeUnit.MILLISECONDS, true);
    TestInterface api = Feign.builder()
        .options(options)
        .client(RibbonClient.create())
        .retryer(Retryer.NEVER_RETRY)
        .target(TestInterface.class, "http://" + client());

    try {
      Response response = api.get();
      assertThat(response.status()).isEqualTo(200);
      assertThat(response.body().toString()).isEqualTo("Hello");
    } catch (Exception ignored) {
      ignored.printStackTrace();
      fail("Shouldn't throw ");
    }

  }

  @Test
  void feignOptionsClientConfig() {
    Request.Options options =
        new Request.Options(1111, TimeUnit.MILLISECONDS, 22222, TimeUnit.MILLISECONDS, true);
    IClientConfig config = new RibbonClient.FeignOptionsClientConfig(options);
    assertThat(config.get(CommonClientConfigKey.ConnectTimeout))
        .isEqualTo(options.connectTimeoutMillis());
    assertThat(config.get(CommonClientConfigKey.ReadTimeout))
        .isEqualTo(options.readTimeoutMillis());
    assertThat(config.get(CommonClientConfigKey.FollowRedirects))
        .isEqualTo(options.isFollowRedirects());
    assertThat(config.getProperties()).hasSize(3);
  }

  @Test
  void cleanUrlWithMatchingHostAndPart() throws IOException {
    URI uri = RibbonClient.cleanUrl("http://questions/questions/answer/123", "questions");
    assertThat(uri.toString()).isEqualTo("http:///questions/answer/123");
  }

  @Test
  void cleanUrl() throws IOException {
    URI uri = RibbonClient.cleanUrl("http://myservice/questions/answer/123", "myservice");
    assertThat(uri.toString()).isEqualTo("http:///questions/answer/123");
  }

  private String client() {
    return testName;
  }

  private String serverListKey() {
    return client() + ".ribbon.listOfServers";
  }

  @AfterEach
  void clearServerList() throws IOException {
    getConfigInstance().clearProperty(serverListKey());
    server2.close();
  }

  interface TestInterface {

    @RequestLine("POST /")
    void post();

    @RequestLine("GET /?a={a}")
    void getWithQueryParameters(@Param("a") String a);

    @RequestLine("GET /")
    Response get();
  }

  @BeforeEach
  void setup(TestInfo testInfo) {
    Optional<Method> testMethod = testInfo.getTestMethod();
    if (testMethod.isPresent()) {
      this.testName = testMethod.get().getName();
    }
  }
}
