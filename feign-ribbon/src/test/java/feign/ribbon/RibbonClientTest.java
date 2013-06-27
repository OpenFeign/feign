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

import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;

import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URL;

import javax.ws.rs.POST;

import feign.Feign;

import static com.netflix.config.ConfigurationManager.getConfigInstance;
import static org.testng.Assert.assertEquals;

@Test
public class RibbonClientTest {
  static interface TestInterface {
    @POST void post();
  }

  @Test
  public void loadBalancingDefaultPolicyRoundRobin() throws IOException, InterruptedException {
    String client = "RibbonClientTest-loadBalancingDefaultPolicyRoundRobin";
    String serverListKey = client + ".ribbon.listOfServers";

    MockWebServer server1 = new MockWebServer();
    server1.enqueue(new MockResponse().setResponseCode(200).setBody("success!".getBytes()));
    server1.play();
    MockWebServer server2 = new MockWebServer();
    server2.enqueue(new MockResponse().setResponseCode(200).setBody("success!".getBytes()));
    server2.play();

    getConfigInstance().setProperty(serverListKey, hostAndPort(server1.getUrl("")) + "," + hostAndPort(server2.getUrl("")));

    try {

      TestInterface api = Feign.create(TestInterface.class, "http://" + client, new RibbonModule());

      api.post();
      api.post();

      assertEquals(server1.getRequestCount(), 1);
      assertEquals(server2.getRequestCount(), 1);
      // TODO: verify ribbon stats match
      // assertEquals(target.lb().getLoadBalancerStats().getSingleServerStat())
    } finally {
      server1.shutdown();
      server2.shutdown();
      getConfigInstance().clearProperty(serverListKey);
    }
  }

  static String hostAndPort(URL url) {
    return url.getHost() + ":" + url.getPort();
  }
}
