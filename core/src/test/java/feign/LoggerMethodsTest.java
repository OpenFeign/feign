/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign;

import static feign.Util.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import feign.Logger.Level;
import java.io.IOException;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class LoggerMethodsTest {

  Logger logger =
      new Logger() {
        @Override
        protected void log(String configKey, String format, Object... args) {}
      };

  @Test
  void responseIsClosedAfterRebuffer() throws IOException {
    Request request =
        Request.create(Request.HttpMethod.GET, "/api", Collections.emptyMap(), null, UTF_8, null);
    Response response =
        Response.builder()
            .status(200)
            .reason("OK")
            .request(request)
            .headers(Collections.emptyMap())
            .body("some text", UTF_8)
            .build();
    Response.Body spyBody = spy(response.body());
    response = response.toBuilder().body(spyBody).build();

    Response rebufferedResponse =
        logger.logAndRebufferResponse("someMethod()", Level.FULL, response, 100);

    verify(spyBody).close();
    assertThat(rebufferedResponse.body()).isNotSameAs(spyBody);
  }
}
