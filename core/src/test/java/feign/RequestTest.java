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

import feign.Request.Body;
import feign.Request.HttpMethod;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class RequestTest {

  @Test
  void stringBodyIsEncodedWithUtf8NotThePlatformDefault() {
    String content = "spécial-çhars-€";

    Body body = Body.create(content);

    assertThat(body.asBytes()).isEqualTo(content.getBytes(UTF_8));
    assertThat(body.getEncoding()).hasValue(UTF_8);
    assertThat(body.isBinary()).isFalse();
    assertThat(body.asString()).isEqualTo(content);
  }

  @Test
  void requestCreatedWithNullBodyReportsNoBody() {
    Request request = requestWithoutBody();

    assertThat(request.body()).isNull();
    assertThat(request.charset()).isNull();
    assertThat(request.isBinary()).isTrue();
    assertThat(request.length()).isZero();
  }

  @Test
  void requestWithoutBodyPrintsOnlyTheRequestLine() {
    Request request = requestWithoutBody();

    assertThat(request).hasToString("GET http://example.com HTTP/1.1\n");
  }

  private static Request requestWithoutBody() {
    return Request.create(
        HttpMethod.GET, "http://example.com", Collections.emptyMap(), (Body) null, null);
  }
}
