/**
 * Copyright 2012-2020 The Feign Authors
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

import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import org.hamcrest.CoreMatchers;
import org.junit.Test;
import java.io.IOException;
import java.util.Arrays;
import feign.Request.Options;

public class CapabilityTest {

  private class AClient implements Client {

    public AClient(Client client) {}

    @Override
    public Response execute(Request request, Options options) throws IOException {
      return null;
    }

  }
  private class BClient implements Client {

    public BClient(Client client) {
      if (!(client instanceof AClient)) {
        throw new RuntimeException("Test is chaing invokations, expected AClient instace here");
      }
    }

    @Override
    public Response execute(Request request, Options options) throws IOException {
      return null;
    }

  }

  @Test
  public void enrichClient() {
    Client enriched = Capability.enrich(new Client.Default(null, null), Arrays.asList(
        new Capability() {
          @Override
          public Client enrich(Client client) {
            return new AClient(client);
          }
        }, new Capability() {
          @Override
          public Client enrich(Client client) {
            return new BClient(client);
          }
        }));

    assertThat(enriched, CoreMatchers.instanceOf(BClient.class));
  }

}
