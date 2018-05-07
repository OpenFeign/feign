/**
 * Copyright 2012-2018 The Feign Authors
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
package feign.hystrix;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import feign.RequestLine;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

public class SetterFactoryTest {

  interface TestInterface {
    @RequestLine("POST /")
    String invoke();
  }

  @Rule
  public final ExpectedException thrown = ExpectedException.none();
  @Rule
  public final MockWebServer server = new MockWebServer();

  @Test
  public void customSetter() {
    thrown.expect(HystrixRuntimeException.class);
    thrown.expectMessage("POST / failed and no fallback available.");

    server.enqueue(new MockResponse().setResponseCode(500));

    SetterFactory commandKeyIsRequestLine = (target, method) -> {
      String groupKey = target.name();
      String commandKey = method.getAnnotation(RequestLine.class).value();
      return HystrixCommand.Setter
          .withGroupKey(HystrixCommandGroupKey.Factory.asKey(groupKey))
          .andCommandKey(HystrixCommandKey.Factory.asKey(commandKey));
    };

    TestInterface api = HystrixFeign.builder()
        .setterFactory(commandKeyIsRequestLine)
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    api.invoke();
  }
}
