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

import static feign.Util.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RetryableExceptionTest {

  @Test
  void createRetryableExceptionWithResponseAndResponseHeader() {
    // given
    Long retryAfter = 5000L;
    Request request =
        Request.create(Request.HttpMethod.GET, "/", Collections.emptyMap(), null, Util.UTF_8);
    byte[] response = "response".getBytes(StandardCharsets.UTF_8);
    Map<String, Collection<String>> responseHeader = new HashMap<>();
    responseHeader.put("TEST_HEADER", Arrays.asList("TEST_CONTENT"));

    // when
    RetryableException retryableException =
        new RetryableException(-1, null, null, retryAfter, request, response, responseHeader);

    // then
    assertThat(retryableException).isNotNull();
    assertThat(retryableException.retryAfter()).isEqualTo(retryAfter);
    assertThat(retryableException.contentUTF8()).isEqualTo(new String(response, UTF_8));
    assertThat(retryableException.responseHeaders()).containsKey("TEST_HEADER");
    assertThat(retryableException.responseHeaders().get("TEST_HEADER")).contains("TEST_CONTENT");
  }
}
