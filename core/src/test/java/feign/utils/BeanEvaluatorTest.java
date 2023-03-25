package feign.utils;

import org.junit.Assert;
import org.junit.Test;

public final class BeanEvaluatorTest {

  @Test
  public void isBean() {
    Assert.assertTrue(BeanEvaluator.isBean(BeanEvaluatorTest.class));
    Assert.assertTrue(!BeanEvaluator.isBean(int.class));
  }
}
