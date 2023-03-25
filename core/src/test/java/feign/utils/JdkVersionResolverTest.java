package feign.utils;

import org.junit.Assert;
import org.junit.Test;

public final class JdkVersionResolverTest {
  @Test
  public void testVersion() {
    Assert.assertTrue(JdkVersionResolver.resolve() > 7);
  }
}
