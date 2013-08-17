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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;

import org.testng.annotations.Test;

import java.util.Collection;

import feign.FeignException;
import feign.Response;
import feign.RetryableException;

import static feign.Util.RETRY_AFTER;

public class DefaultErrorDecoderTest {
  ErrorDecoder errorDecoder = new ErrorDecoder.Default();

  @Test(expectedExceptions = FeignException.class, expectedExceptionsMessageRegExp = "status 500 reading Service#foo\\(\\)")
  public void throwsFeignException() throws Throwable {
    Response response = Response.create(500, "Internal server error", ImmutableMap.<String, Collection<String>>of(),
        null);

    throw errorDecoder.decode("Service#foo()", response);
  }

  @Test(expectedExceptions = FeignException.class, expectedExceptionsMessageRegExp = "status 500 reading Service#foo\\(\\); content:\nhello world")
  public void throwsFeignExceptionIncludingBody() throws Throwable {
    Response response = Response.create(500, "Internal server error", ImmutableMap.<String, Collection<String>>of(),
        "hello world");

    throw errorDecoder.decode("Service#foo()", response);
  }

  @Test(expectedExceptions = RetryableException.class, expectedExceptionsMessageRegExp = "status 503 reading Service#foo\\(\\)")
  public void retryAfterHeaderThrowsRetryableException() throws Throwable {
    Response response = Response.create(503, "Service Unavailable",
        ImmutableMultimap.of(RETRY_AFTER, "Sat, 1 Jan 2000 00:00:00 GMT").asMap(), null);

    throw errorDecoder.decode("Service#foo()", response);
  }
}
