package feign.ribbon;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;

public class LBClientTest {

  @Test
  public void testParseCodes() {
    assertEquals(Collections.emptySet(), LBClient.parseStatusCodes(""));
    assertEquals(Collections.emptySet(), LBClient.parseStatusCodes(null));
    assertEquals(Collections.singleton(504), LBClient.parseStatusCodes("504"));
    assertEquals(new LinkedHashSet<Integer>(Arrays.asList(503, 504)), LBClient.parseStatusCodes("503,504"));
  }
}
