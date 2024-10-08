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
package feign.codec;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("deprecation")
public class DefaultErrorDecoderHttpErrorTest {

  public static Object[][] errorCodes() {
    return new Object[][] {
      {
        400,
        FeignException.BadRequest.class,
        "[400 anything] during [GET] to [http://example.com/api] [Service#foo()]: [response body]"
      },
      {
        401,
        FeignException.Unauthorized.class,
        "[401 anything] during [GET] to [http://example.com/api] [Service#foo()]: [response body]"
      },
      {
        403,
        FeignException.Forbidden.class,
        "[403 anything] during [GET] to [http://example.com/api] [Service#foo()]: [response body]"
      },
      {
        404,
        FeignException.NotFound.class,
        "[404 anything] during [GET] to [http://example.com/api] [Service#foo()]: [response body]"
      },
      {
        405,
        FeignException.MethodNotAllowed.class,
        "[405 anything] during [GET] to [http://example.com/api] [Service#foo()]: [response body]"
      },
      {
        406,
        FeignException.NotAcceptable.class,
        "[406 anything] during [GET] to [http://example.com/api] [Service#foo()]: [response body]"
      },
      {
        409,
        FeignException.Conflict.class,
        "[409 anything] during [GET] to [http://example.com/api] [Service#foo()]: [response body]"
      },
      {
        429,
        FeignException.TooManyRequests.class,
        "[429 anything] during [GET] to [http://example.com/api] [Service#foo()]: [response body]"
      },
      {
        422,
        FeignException.UnprocessableEntity.class,
        "[422 anything] during [GET] to [http://example.com/api] [Service#foo()]: [response body]"
      },
      {
        450,
        FeignException.FeignClientException.class,
        "[450 anything] during [GET] to [http://example.com/api] [Service#foo()]: [response body]"
      },
      {
        500,
        FeignException.InternalServerError.class,
        "[500 anything] during [GET] to [http://example.com/api] [Service#foo()]: [response body]"
      },
      {
        501,
        FeignException.NotImplemented.class,
        "[501 anything] during [GET] to [http://example.com/api] [Service#foo()]: [response body]"
      },
      {
        502,
        FeignException.BadGateway.class,
        "[502 anything] during [GET] to [http://example.com/api] [Service#foo()]: [response body]"
      },
      {
        503,
        FeignException.ServiceUnavailable.class,
        "[503 anything] during [GET] to [http://example.com/api] [Service#foo()]: [response body]"
      },
      {
        504,
        FeignException.GatewayTimeout.class,
        "[504 anything] during [GET] to [http://example.com/api] [Service#foo()]: [response body]"
      },
      {
        599,
        FeignException.FeignServerException.class,
        "[599 anything] during [GET] to [http://example.com/api] [Service#foo()]: [response body]"
      },
      {
        599,
        FeignException.class,
        "[599 anything] during [GET] to [http://example.com/api] [Service#foo()]: [response body]"
      },
    };
  }

  public int httpStatus;
  public Class expectedExceptionClass;
  public String expectedMessage;

  private ErrorDecoder errorDecoder = new ErrorDecoder.Default();

  private Map<String, Collection<String>> headers = new LinkedHashMap<>();

  @MethodSource("errorCodes")
  @ParameterizedTest(name = "error: [{0}], exception: [{1}]")
  void exceptionIsHttpSpecific(int httpStatus, Class expectedExceptionClass, String expectedMessage)
      throws Throwable {
    initDefaultErrorDecoderHttpErrorTest(httpStatus, expectedExceptionClass, expectedMessage);
    Response response =
        Response.builder()
            .status(httpStatus)
            .reason("anything")
            .request(
                Request.create(
                    HttpMethod.GET,
                    "http://example.com/api",
                    Collections.emptyMap(),
                    null,
                    Util.UTF_8))
            .headers(headers)
            .body("response body", Util.UTF_8)
            .build();

    Exception exception = errorDecoder.decode("Service#foo()", response);

    assertThat(exception).isInstanceOf(expectedExceptionClass);
    assertThat(((FeignException) exception).status()).isEqualTo(httpStatus);
    assertThat(exception.getMessage()).isEqualTo(expectedMessage);
  }

  public void initDefaultErrorDecoderHttpErrorTest(
      int httpStatus, Class expectedExceptionClass, String expectedMessage) {
    this.httpStatus = httpStatus;
    this.expectedExceptionClass = expectedExceptionClass;
    this.expectedMessage = expectedMessage;
  }
}
