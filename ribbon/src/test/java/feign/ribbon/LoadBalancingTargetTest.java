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
import java.io.IOException;
import java.net.URL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import feign.Feign;
import feign.RequestLine;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;

public class LoadBalancingTargetTest {

  public final MockWebServer server1 = new MockWebServer();
  public final MockWebServer server2 = new MockWebServer();

  static String hostAndPort(URL url) {
    // our build slaves have underscores in their hostnames which aren't permitted by ribbon
    return "localhost:" + url.getPort();
  }

  @Test
  void loadBalancingDefaultPolicyRoundRobin() throws IOException, InterruptedException {
    String name = "LoadBalancingTargetTest-loadBalancingDefaultPolicyRoundRobin";
    String serverListKey = name + ".ribbon.listOfServers";

    server1.enqueue(new MockResponse().setBody("success!"));
    server2.enqueue(new MockResponse().setBody("success!"));

    getConfigInstance().setProperty(serverListKey,
        hostAndPort(server1.url("").url()) + "," + hostAndPort(
            server2.url("").url()));

    try {
      LoadBalancingTarget<TestInterface> target =
          LoadBalancingTarget.create(TestInterface.class, "http://" + name);
      TestInterface api = Feign.builder().target(target);

      api.post();
      api.post();

      assertThat(server1.getRequestCount()).isEqualTo(1);
      assertThat(server2.getRequestCount()).isEqualTo(1);
      // TODO: verify ribbon stats match
      // assertEquals(target.lb().getLoadBalancerStats().getSingleServerStat())
    } finally {
      getConfigInstance().clearProperty(serverListKey);
    }
  }

  @Test
  void loadBalancingTargetPath() throws InterruptedException {
    String name = "LoadBalancingTargetTest-loadBalancingDefaultPolicyRoundRobin";
    String serverListKey = name + ".ribbon.listOfServers";

    server1.enqueue(new MockResponse().setBody("success!"));

    getConfigInstance().setProperty(serverListKey,
        hostAndPort(server1.url("").url()));

    try {
      LoadBalancingTarget<TestInterface> target =
          LoadBalancingTarget.create(TestInterface.class, "http://" + name + "/context-path");
      TestInterface api = Feign.builder().target(target);

      api.get();

      assertThat(target.url()).isEqualTo("http:///context-path");
      assertThat(server1.takeRequest().getPath()).isEqualTo("/context-path/servers");
    } finally {
      getConfigInstance().clearProperty(serverListKey);
    }
  }

  interface TestInterface {

    @RequestLine("POST /")
    void post();

    @RequestLine("GET /servers")
    void get();
  }

  @AfterEach
  void afterEachTest() throws IOException {
    server2.close();
  }
}
