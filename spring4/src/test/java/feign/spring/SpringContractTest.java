/**
 * Copyright 2012-2019 The Feign Authors
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
package feign.spring;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import org.junit.Before;
import org.junit.Test;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import feign.Feign;
import feign.Request;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.mock.HttpMethod;
import feign.mock.MockClient;

public class SpringContractTest {

  private MockClient mockClient;
  private HealthResource resource;

  @Before
  public void setup() throws IOException {
    mockClient = new MockClient()
        .noContent(HttpMethod.GET, "mock:///health")
        .noContent(HttpMethod.GET, "mock:///health/1?deep=true")
        .noContent(HttpMethod.GET, "mock:///health/1?deep=true&dryRun=true")
        .ok(HttpMethod.GET, "mock:///health/generic", "{}");
    resource = Feign.builder()
        .contract(new SpringContract())
        .encoder(new JacksonEncoder())
        .decoder(new JacksonDecoder())
        .client(mockClient)
        .target(HealthResource.class, "mock://");
  }

  @Test
  public void requestParam() {
    resource.check("1", true);

    mockClient.verifyOne(HttpMethod.GET, "mock:///health/1?deep=true");
  }

  @Test
  public void requestTwoParams() {
    resource.check("1", true, true);

    mockClient.verifyOne(HttpMethod.GET, "mock:///health/1?deep=true&dryRun=true");
  }

  @Test
  public void inheritance() {
    final Data data = resource.getData(new Data());
    assertThat(data, notNullValue());

    final Request request = mockClient.verifyOne(HttpMethod.GET, "mock:///health/generic");
    assertThat(request.headers(), equalTo(
        Collections.singletonMap("Content-Type", Arrays.asList("application/json"))));
  }

}
