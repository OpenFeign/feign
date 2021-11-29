package feign;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;

import static feign.Util.enumForName;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class EnumForNameTest {

  @RunWith(Parameterized.class)
  public static class KnownEnumValues {

    @Parameter(0)
    public Class clazz;
    @Parameter(1)
    public Object name;

    @Parameters
    public static Iterable<Object[]> data() {
      return Arrays.asList(new Object[][] {
          {Request.ProtocolVersion.class, Request.ProtocolVersion.HTTP_1_0},
          {Request.ProtocolVersion.class, "HTTP/1.0"},
          {Request.ProtocolVersion.class, Request.ProtocolVersion.HTTP_1_1},
          {Request.ProtocolVersion.class, "HTTP/1.1"},
          {Request.ProtocolVersion.class, Request.ProtocolVersion.HTTP_2},
          {Request.ProtocolVersion.class, "HTTP/2.0"}
      });
    }

    @Test
    public void getKnownEnumValue() {
      assertNotNull("known enum value: " + name, enumForName(clazz, name));
    }

  }

  @RunWith(Parameterized.class)
  public static class UnknownEnumValues {

    @Parameter(0)
    public Class clazz;
    @Parameter(1)
    public Object name;

    @Parameters
    public static Iterable<Object[]> data() {
      return Arrays.asList(new Object[][] {
          {Request.ProtocolVersion.class, Request.HttpMethod.GET},
          {Request.ProtocolVersion.class, "SPDY/3"},
          {Request.ProtocolVersion.class, null},
          {Request.ProtocolVersion.class, "HTTP/2"}
      });
    }

    @Test
    public void getKnownEnumValue() {
      assertNull("unknown enum value: " + name, enumForName(clazz, name));
    }

  }

}
