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
package feign.codec;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import feign.FeignException;
import feign.Response;

import static feign.Util.RETRY_AFTER;
import static feign.Util.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class DefaultErrorDecoderTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  ErrorDecoder errorDecoder = new ErrorDecoder.Default();

  Map<String, Collection<String>> headers = new LinkedHashMap<String, Collection<String>>();

  @Test
  public void throwsFeignException() throws Throwable {
    thrown.expect(FeignException.class);
    thrown.expectMessage("status 500 reading Service#foo()");

    Response response = Response.create(500, "Internal server error", headers, (byte[]) null);

    throw errorDecoder.decode("Service#foo()", response);
  }

  @Test
  public void throwsFeignExceptionIncludingBody() throws Throwable {
    thrown.expect(FeignException.class);
    thrown.expectMessage("status 500 reading Service#foo(); content:\nhello world");

    Response response = Response.create(500, "Internal server error", headers, "hello world", UTF_8);

    throw errorDecoder.decode("Service#foo()", response);
  }

  @Test
  public void testFeignExceptionIncludesStatus() throws Throwable {
    Response response = Response.create(400, "Bad request", headers, (byte[]) null);

    Exception exception = errorDecoder.decode("Service#foo()", response);

    assertThat(exception).isInstanceOf(FeignException.class);
    assertThat(((FeignException) exception).status()).isEqualTo(400);
  }

  @Test
  public void retryAfterHeaderThrowsRetryableException() throws Throwable {
    thrown.expect(FeignException.class);
    thrown.expectMessage("status 503 reading Service#foo()");

    headers.put(RETRY_AFTER, Arrays.asList("Sat, 1 Jan 2000 00:00:00 GMT"));
    Response response = Response.create(503, "Service Unavailable", headers, (byte[]) null);

    throw errorDecoder.decode("Service#foo()", response);
  }
}
