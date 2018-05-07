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
package feign.benchmark;

import java.util.List;
import feign.Body;
import feign.Headers;
import feign.Param;
import feign.RequestLine;
import feign.Response;

@Headers("Accept: application/json")
interface FeignTestInterface {

  @RequestLine("GET /?Action=GetUser&Version=2010-05-08&limit=1")
  Response query();

  @RequestLine("GET /domains/{domainId}/records?name={name}&type={type}")
  Response mixedParams(@Param("domainId") int id,
                       @Param("name") String nameFilter,
                       @Param("type") String typeFilter);

  @RequestLine("PATCH /")
  Response customMethod();

  @RequestLine("PUT /")
  @Headers("Content-Type: application/json")
  void bodyParam(List<String> body);

  @RequestLine("POST /")
  @Body("%7B\"customer_name\": \"{customer_name}\", \"user_name\": \"{user_name}\", \"password\": \"{password}\"%7D")
  void form(@Param("customer_name") String customer,
            @Param("user_name") String user,
            @Param("password") String password);

  @RequestLine("POST /")
  @Headers({"Happy: sad", "Auth-Token: {authToken}"})
  void headers(@Param("authToken") String token);
}
