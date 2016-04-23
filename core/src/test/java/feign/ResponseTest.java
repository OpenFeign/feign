/*
 * Copyright 2013 Netflix, Inc.
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

import org.junit.Test;

import java.util.Collection;
import java.util.Collections;

import static feign.assertj.FeignAssertions.assertThat;

public class ResponseTest {

  @Test
  public void reasonPhraseIsOptional() {
    Response response = Response.create(200, null /* reason phrase */, Collections.
        <String, Collection<String>>emptyMap(), new byte[0]);

    assertThat(response.reason()).isNull();
    assertThat(response.toString()).isEqualTo("HTTP/1.1 200\n\n");
  }
}
