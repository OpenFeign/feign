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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import feign.Request.HttpMethod;
import feign.Target.EmptyTarget;
import java.net.URI;
import org.junit.jupiter.api.Test;

class EmptyTargetTest {

  @Test
  void whenNameNotSupplied() {
    assertThat(EmptyTarget.create(UriInterface.class))
        .isEqualTo(EmptyTarget.create(UriInterface.class, "empty:UriInterface"));
  }

  @Test
  void toString_withoutName() {
    assertThat(EmptyTarget.create(UriInterface.class).toString())
        .isEqualTo("EmptyTarget(type=UriInterface)");
  }

  @Test
  void toString_withName() {
    assertThat(EmptyTarget.create(UriInterface.class, "manager-access").toString())
        .isEqualTo("EmptyTarget(type=UriInterface, name=manager-access)");
  }

  @Test
  void mustApplyToAbsoluteUrl() {
    UnsupportedOperationException exception =
        assertThrows(
            UnsupportedOperationException.class,
            () ->
                EmptyTarget.create(UriInterface.class)
                    .apply(new RequestTemplate().method(HttpMethod.GET).uri("/relative")));
    assertThat(exception.getMessage())
        .isEqualTo("Request with non-absolute URL not supported with empty target");
  }

  interface UriInterface {

    @RequestLine("GET /")
    Response get(URI endpoint);
  }
}
