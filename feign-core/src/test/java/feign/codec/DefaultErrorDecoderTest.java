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
