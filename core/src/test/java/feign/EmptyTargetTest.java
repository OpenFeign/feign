/*
 * Copyright 2015 Netflix, Inc.
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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.net.URI;

import feign.Target.EmptyTarget;

import static feign.assertj.FeignAssertions.assertThat;

public class EmptyTargetTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void whenNameNotSupplied() {
    assertThat(EmptyTarget.create(UriInterface.class))
        .isEqualTo(EmptyTarget.create(UriInterface.class, "empty:UriInterface"));
  }

  @Test
  public void toString_withoutName() {
    assertThat(EmptyTarget.create(UriInterface.class).toString())
        .isEqualTo("EmptyTarget(type=UriInterface)");
  }

  @Test
  public void toString_withName() {
    assertThat(EmptyTarget.create(UriInterface.class, "manager-access").toString())
        .isEqualTo("EmptyTarget(type=UriInterface, name=manager-access)");
  }

  @Test
  public void mustApplyToAbsoluteUrl() {
    thrown.expect(UnsupportedOperationException.class);
    thrown.expectMessage("Request with non-absolute URL not supported with empty target");

    EmptyTarget.create(UriInterface.class)
        .apply(new RequestTemplate().method("GET").append("/relative"));
  }

  interface UriInterface {

    @RequestLine("GET /")
    Response get(URI endpoint);
  }
}
