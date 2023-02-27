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
package feign;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import java.util.Arrays;
import feign.Request.ProtocolVersion;
import static feign.Util.enumForName;
import static org.junit.Assert.*;

public class EnumForNameTest {

  @RunWith(Parameterized.class)
  public static class KnownEnumValues {

    @Parameter
    public Object name;
    @Parameter(1)
    public ProtocolVersion expectedProtocolVersion;

    @Parameters
    public static Iterable<Object[]> data() {
      return Arrays.asList(new Object[][] {
          {ProtocolVersion.HTTP_1_0, ProtocolVersion.HTTP_1_0},
          {"HTTP/1.0", ProtocolVersion.HTTP_1_0},
          {ProtocolVersion.HTTP_1_1, ProtocolVersion.HTTP_1_1},
          {"HTTP/1.1", ProtocolVersion.HTTP_1_1},
          {ProtocolVersion.HTTP_2, ProtocolVersion.HTTP_2},
          {"HTTP/2.0", ProtocolVersion.HTTP_2}
      });
    }

    @Test
    public void getKnownEnumValue() {
      assertEquals("known enum value: " + name, expectedProtocolVersion,
          enumForName(ProtocolVersion.class, name));
    }

  }

  @RunWith(Parameterized.class)
  public static class UnknownEnumValues {

    @Parameter
    public Object name;

    @Parameters
    public static Iterable<Object[]> data() {
      return Arrays.asList(new Object[][] {
          {Request.HttpMethod.GET},
          {"SPDY/3"},
          {null},
          {"HTTP/2"}
      });
    }

    @Test
    public void getKnownEnumValue() {
      assertNull("unknown enum value: " + name, enumForName(ProtocolVersion.class, name));
    }

  }

}
