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
package feign.codec;

import static feign.Util.RETRY_AFTER;
import static feign.Util.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import feign.FeignException;
import feign.Request;
import feign.Request.HttpMethod;
import feign.Response;
import feign.Util;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class DefaultErrorDecoderTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  private ErrorDecoder errorDecoder = new ErrorDecoder.Default();

  private Map<String, Collection<String>> headers = new LinkedHashMap<>();

  @Test
  public void throwsFeignException() throws Throwable {
    thrown.expect(FeignException.class);
    thrown.expectMessage("status 500 reading Service#foo()");

    Response response = Response.builder()
        .status(500)
        .reason("Internal server error")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(headers)
        .build();

    throw errorDecoder.decode("Service#foo()", response);
  }

  @Test
  public void throwsFeignExceptionIncludingBody() throws Throwable {
    Response response = Response.builder()
        .status(500)
        .reason("Internal server error")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(headers)
        .body("hello world", UTF_8)
        .build();

    try {
      throw errorDecoder.decode("Service#foo()", response);
    } catch (FeignException e) {
      assertThat(e.getMessage()).isEqualTo("status 500 reading Service#foo()");
      assertThat(e.contentUTF8()).isEqualTo("hello world");
    }
  }

  @Test
  public void testFeignExceptionIncludesStatus() {
    Response response = Response.builder()
        .status(400)
        .reason("Bad request")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(headers)
        .build();

    Exception exception = errorDecoder.decode("Service#foo()", response);

    assertThat(exception).isInstanceOf(FeignException.class);
    assertThat(((FeignException) exception).status()).isEqualTo(400);
  }

  @Test
  public void retryAfterHeaderThrowsRetryableException() throws Throwable {
    thrown.expect(FeignException.class);
    thrown.expectMessage("status 503 reading Service#foo()");

    headers.put(RETRY_AFTER, Collections.singletonList("Sat, 1 Jan 2000 00:00:00 GMT"));
    Response response = Response.builder()
        .status(503)
        .reason("Service Unavailable")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(headers)
        .build();

    throw errorDecoder.decode("Service#foo()", response);
  }
}
