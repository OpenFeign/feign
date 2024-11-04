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
package feign.vertx.testcase;

import feign.Headers;
import feign.RequestLine;
import feign.Response;
import io.vertx.core.Future;

/**
 * Example of an API to to test number of Http2 connections of Feign.
 *
 * @author James Xu
 */
@Headers({"Accept: application/json"})
public interface HelloServiceAPI {

  @RequestLine("GET /hello")
  Future<Response> hello();
}