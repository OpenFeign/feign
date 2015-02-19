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
