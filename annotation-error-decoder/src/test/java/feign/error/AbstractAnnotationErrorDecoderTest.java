/**
 * Copyright 2017-2019 The Feign Authors
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
package feign.error;

import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import static feign.Feign.configKey;

public abstract class AbstractAnnotationErrorDecoderTest<T> {



  public abstract Class<T> interfaceAtTest();

  String feignConfigKey(String methodName) throws NoSuchMethodException {
    return configKey(interfaceAtTest(), interfaceAtTest().getMethod(methodName));
  }

  Response testResponse(int status) {
    return testResponse(status, "default Response body");
  }

  Response testResponse(int status, String body) {
    return testResponse(status, body, new HashMap<String, Collection<String>>());
  }

  Response testResponse(int status, String body, Map<String, Collection<String>> headers) {
    return Response.builder()
        .status(status)
        .body(body, StandardCharsets.UTF_8)
        .headers(headers)
        .request(Request.create(Request.HttpMethod.GET, "http://test", headers,
            Request.Body.empty(), null))
        .build();
  }
}
