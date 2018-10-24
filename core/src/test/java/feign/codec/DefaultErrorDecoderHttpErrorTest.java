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

import feign.FeignException;
import feign.Request;
import feign.Request.HttpMethod;
import feign.Response;
import feign.Util;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class DefaultErrorDecoderHttpErrorTest {

  @Parameterized.Parameters(name = "error: [{0}], exception: [{1}]")
  public static Object[][] errorCodes() {
    return new Object[][] {
        {400, FeignException.BadRequest.class},
        {401, FeignException.Unauthorized.class},
        {403, FeignException.Forbidden.class},
        {404, FeignException.NotFound.class},
        {405, FeignException.MethodNotAllowed.class},
        {406, FeignException.NotAcceptable.class},
        {409, FeignException.Conflict.class},
        {429, FeignException.TooManyRequests.class},
        {422, FeignException.UnprocessableEntity.class},
        {500, FeignException.InternalServerError.class},
        {501, FeignException.NotImplemented.class},
        {502, FeignException.BadGateway.class},
        {503, FeignException.ServiceUnavailable.class},
        {504, FeignException.GatewayTimeout.class},
        {599, FeignException.class},
    };
  }

  @Parameterized.Parameter
  public int httpStatus;

  @Parameterized.Parameter(1)
  public Class expectedExceptionClass;

  private ErrorDecoder errorDecoder = new ErrorDecoder.Default();

  private Map<String, Collection<String>> headers = new LinkedHashMap<>();

  @Test
  public void testExceptionIsHttpSpecific() throws Throwable {
    Response response = Response.builder()
        .status(httpStatus)
        .reason("anything")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(headers)
        .build();

    Exception exception = errorDecoder.decode("Service#foo()", response);

    assertThat(exception).isInstanceOf(expectedExceptionClass);
    assertThat(((FeignException) exception).status()).isEqualTo(httpStatus);
  }

}
