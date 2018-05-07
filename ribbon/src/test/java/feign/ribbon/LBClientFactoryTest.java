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

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import com.netflix.client.ClientFactory;

public class LBClientFactoryTest {

  @Test
  public void testCreateLBClient() {
    LBClientFactory.Default lbClientFactory = new LBClientFactory.Default();
    LBClient client = lbClientFactory.create("clientName");
    assertEquals("clientName", client.getClientName());
    assertEquals(ClientFactory.getNamedLoadBalancer("clientName"), client.getLoadBalancer());
  }
}
