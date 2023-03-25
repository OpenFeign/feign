package feign.utils;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import org.junit.Assert;
import org.junit.Test;

public final class RecordInvokeUtilsTest {
  @Target(ElementType.RECORD_COMPONENT)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface RecordComponentTest {
    String value();
  }

  private record TestRecord(
                            @RecordComponentTest("value1") String name,
                            @RecordComponentTest("value2") int age) {}

  @Test
  public void isRecord() {
    Assert.assertTrue(RecordInvokeUtils.isRecord(TestRecord.class));
    Assert.assertTrue(!RecordInvokeUtils.isRecord(RecordInvokeUtilsTest.class));
  }

  @Test
  public void componentValue() {
    TestRecord t = new TestRecord("tom", 10);
    RecComponent recComponent1 = new RecComponent("name", String.class, 0, null);

    String name = (String) RecordInvokeUtils.componentValue(t, recComponent1);
    Assert.assertEquals(name, "tom");

    RecComponent recComponent2 = new RecComponent("age", int.class, 0, null);

    int age = (int) RecordInvokeUtils.componentValue(t, recComponent2);
    Assert.assertEquals(age, 10);
  }

  @Test
  public void recordComponents() {
    RecComponent[] expected = {
        new RecComponent("name", String.class, 0, null),
        new RecComponent("age", int.class, 1, null)
    };
    RecComponent[] arr = RecordInvokeUtils.recordComponents(TestRecord.class, null);

    Assert.assertEquals(arr[0].name(), expected[0].name());
    Assert.assertEquals(arr[0].type(), expected[0].type());
    Assert.assertEquals(arr[0].index(), expected[0].index());
    Assert.assertEquals(
        ((RecordComponentTest) arr[0].annotations()[0]).value(), "value1");

    Assert.assertEquals(arr[1].name(), expected[1].name());
    Assert.assertEquals(arr[1].type(), expected[1].type());
    Assert.assertEquals(arr[1].index(), expected[1].index());
    Assert.assertEquals(
        ((RecordComponentTest) arr[1].annotations()[0]).value(), "value2");

  }

}
