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
package feign.mock;

import feign.Feign;
import feign.RequestLine;
import feign.Response;
import org.junit.Test;
import static org.junit.Assert.*;

public class HttpProtocolVersionTest {

  interface Remote {

    @RequestLine("GET /test")
    Response test();

  }

  @Test
  public void testMockProtocolVersion() {
    Remote remote = Feign.builder()
        .client(new MockClient().ok(HttpMethod.GET, "/test"))
        .target(new MockTarget<>(Remote.class));

    Response response = remote.test();

    assertNotNull(response.protocolVersion());
    assertEquals("MOCK", response.protocolVersion().toString());
  }

}
