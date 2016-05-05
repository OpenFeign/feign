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
package feign.ribbon;

import static com.netflix.config.ConfigurationManager.getConfigInstance;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.URL;

import org.junit.After;
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
import feign.RequestLine;
import feign.client.TrustingSSLSocketFactory;

public class RibbonClientTest {

  @Rule
  public final TestName testName = new TestName();
  @Rule
  public final MockWebServer server1 = new MockWebServer();
  @Rule
  public final MockWebServer server2 = new MockWebServer();

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

    TestInterface
        api =
        Feign.builder().client(RibbonClient.create())
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

    TestInterface
        api =
        Feign.builder().client(RibbonClient.create())
            .target(TestInterface.class, "http://" + client());

    api.post();

    assertEquals(2, server1.getRequestCount());
    // TODO: verify ribbon stats match
    // assertEquals(target.lb().getLoadBalancerStats().getSingleServerStat())
  }

  /*
          This test-case replicates a bug that occurs when using RibbonRequest with a query string.

          The querystrings would not be URL-encoded, leading to invalid HTTP-requests if the query string contained
          invalid characters (ex. space).
   */
  @Test
  public void urlEncodeQueryStringParameters() throws IOException, InterruptedException {
    String queryStringValue = "some string with space";
    String expectedQueryStringValue = "some+string+with+space";
    String expectedRequestLine = String.format("GET /?a=%s HTTP/1.1", expectedQueryStringValue);

    server1.enqueue(new MockResponse().setBody("success!"));

    getConfigInstance().setProperty(serverListKey(), hostAndPort(server1.url("").url()));

    TestInterface
        api =
        Feign.builder().client(RibbonClient.create())
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
  public void testFeignOptionsClientConfig() {
    Request.Options options = new Request.Options(1111, 22222);
    IClientConfig config = new RibbonClient.FeignOptionsClientConfig(options);
    assertThat(config.get(CommonClientConfigKey.ConnectTimeout),
        equalTo(options.connectTimeoutMillis()));
    assertThat(config.get(CommonClientConfigKey.ReadTimeout), equalTo(options.readTimeoutMillis()));
    assertEquals(2, config.getProperties().size());
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
  }
}
