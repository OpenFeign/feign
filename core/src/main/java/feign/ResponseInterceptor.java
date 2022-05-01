/*
 * Copyright 2012-2022 The Feign Authors
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
package feign;

/**
 * Zero or more {@code ResponseInterceptor} may be configured for purposes such as verify or modify
 * headers of response, verify the business status of decoded object. No guarantees are given with
 * regards to the order that interceptors are applied. Once interceptors are applied,
 * {@link ResponseInterceptor#beforeDecode(Response)} is called before decode method called,
 * {@link ResponseInterceptor#afterDecode(Object)} is called after decode method called.
 */
public interface ResponseInterceptor {

  /**
   * Called for response before decode, add data on the supplied {@link Response} or doing
   * customized logic
   *
   * @param response
   * @return
   */
  void beforeDecode(Response response);

  /**
   * Called for response after decode, add data to decoded object or doing customized logic
   *
   * @param response
   * @return
   */
  void afterDecode(Object response);
}
