/*
 * Copyright 2012-2023 The Feign Authors
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
 * Zero or One {@code ResponseInterceptor} may be configured for purposes such as verify or modify
 * headers of response, verify the business status of decoded object. Once the interceptor is
 * applied, {@link ResponseInterceptor#aroundDecode(InvocationContext)} is called around decode
 * method called
 */
public interface ResponseInterceptor {

  ResponseInterceptor DEFAULT = InvocationContext::proceed;

  /**
   * Called by {@link ResponseHandler} after refreshing the response and wrapped around the whole
   * decode process, must either manually invoke {@link InvocationContext#proceed} or manually
   * create a new response object
   *
   * @param invocationContext information surrounding the response being decoded
   * @return decoded response
   */
  Object aroundDecode(InvocationContext invocationContext) throws Exception;
}
