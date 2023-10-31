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
 * {@code ResponseInterceptor}s may be configured for purposes such as verifying or modifying
 * headers of response, verifying the business status of decoded object, or processing responses to
 * unsuccessful requests. Once the interceptors are applied,
 * {@link ResponseInterceptor#intercept(InvocationContext, Chain)} is called, then the response is
 * decoded.
 */
public interface ResponseInterceptor {

  /**
   * Called by {@link ResponseHandler} after refreshing the response and wrapped around the whole
   * decode process, must either manually invoke {@link Chain#next(InvocationContext)} or manually
   * create a new response object
   *
   * @param invocationContext information surrounding the response being decoded
   * @return decoded response
   */
  Object intercept(InvocationContext invocationContext, Chain chain) throws Exception;

  /**
   * Return a new {@link ResponseInterceptor} that invokes the current interceptor first and then
   * the one that is passed in.
   * 
   * @param nextInterceptor the interceptor to delegate to after the current
   * @return a new interceptor that chains the two
   */
  default ResponseInterceptor andThen(ResponseInterceptor nextInterceptor) {
    return (ic, chain) -> intercept(ic,
        nextContext -> nextInterceptor.intercept(nextContext, chain));
  }

  /**
   * Contract for delegation to the rest of the chain.
   */
  interface Chain {
    Chain DEFAULT = InvocationContext::proceed;

    /**
     * Delegate to the rest of the chain to execute the request.
     * 
     * @param context the request to execute the {@link Chain} .
     * @return the response
     */
    Object next(InvocationContext context) throws Exception;
  }

  /**
   * Apply this interceptor to the given {@code Chain} resulting in an intercepted chain.
   * 
   * @param chain the chain to add interception around
   * @return a new chain instance
   */
  default Chain apply(Chain chain) {
    return request -> intercept(request, chain);
  }
}
