/**
 * Copyright 2012-2021 The Feign Authors
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
