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

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.reflect.TypeToken;

import org.testng.annotations.Test;

import feign.FeignException;
import feign.Response;
import feign.RetryableException;

import static com.google.common.net.HttpHeaders.RETRY_AFTER;

public class DefaultErrorDecoderTest {
  @Test(expectedExceptions = FeignException.class, expectedExceptionsMessageRegExp = "status 500 reading Service#foo\\(\\)")
  public void throwsFeignException() throws Throwable {
    Response response = Response.create(500, "Internal server error", ImmutableListMultimap.<String, String>of(),
        null);

    ErrorDecoder.DEFAULT.decode("Service#foo()", response, TypeToken.of(Void.class));
  }

  @Test(expectedExceptions = FeignException.class, expectedExceptionsMessageRegExp = "status 500 reading Service#foo\\(\\); content:\nhello world")
  public void throwsFeignExceptionIncludingBody() throws Throwable {
    Response response = Response.create(500, "Internal server error", ImmutableListMultimap.<String, String>of(),
        "hello world");

    ErrorDecoder.DEFAULT.decode("Service#foo()", response, TypeToken.of(Void.class));
  }

  @Test(expectedExceptions = RetryableException.class, expectedExceptionsMessageRegExp = "status 503 reading Service#foo\\(\\)")
  public void retryAfterHeaderThrowsRetryableException() throws Throwable {
    Response response = Response.create(503, "Service Unavailable",
        ImmutableListMultimap.of(RETRY_AFTER, "Sat, 1 Jan 2000 00:00:00 GMT"), null);

    ErrorDecoder.DEFAULT.decode("Service#foo()", response, TypeToken.of(Void.class));
  }
}
