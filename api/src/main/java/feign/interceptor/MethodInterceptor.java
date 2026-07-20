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
package feign.interceptor;

import feign.Contract;
import feign.Experimental;
import feign.MethodMetadata;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Response;
import feign.ResponseInterceptor;

/**
 * An around-style interceptor invoked once per method call, after the {@link Contract} has resolved
 * the {@link RequestTemplate} from method arguments and before {@link RequestInterceptor}s mutate
 * it. Implementations have full access to the raw method arguments, the {@link MethodMetadata}, the
 * resolved {@link RequestTemplate} (headers, query params, body bytes), and after {@link
 * Chain#next(Invocation)} completes successfully, the {@link Response} via {@link
 * Invocation#response()}.
 *
 * <p>Use this extension point when {@link RequestInterceptor} or {@link ResponseInterceptor} are
 * not enough — typically when you need both the typed argument objects (e.g. for validation) and
 * the resolved request, or when you need to short-circuit the entire HTTP exchange and return a
 * value without sending a request.
 *
 * <p><b>Short-circuit:</b> simply return a value without calling {@link Chain#next(Invocation)}.
 *
 * <p><b>Exception propagation:</b> any {@link Throwable} thrown surfaces to the caller of the Feign
 * interface method, after retry handling.
 *
 * <pre>
 * public Object intercept(Invocation invocation, Chain chain) throws Throwable {
 *   validate(invocation.arguments());
 *   return chain.next(invocation);
 * }
 * </pre>
 */
@Experimental
public interface MethodInterceptor {

  /**
   * Called for every method invocation on a Feign-built proxy.
   *
   * @param invocation context for the current method call
   * @param chain delegate to invoke the rest of the pipeline (request interceptors, HTTP, response
   *     interceptors, decoder)
   * @return the value to return from the proxied method
   */
  Object intercept(Invocation invocation, Chain chain) throws Throwable;

  /**
   * Apply this interceptor to the given chain, producing a chain that runs this interceptor first.
   */
  default Chain apply(Chain chain) {
    return invocation -> intercept(invocation, chain);
  }

  /**
   * Returns a new {@link MethodInterceptor} that invokes the current interceptor first and then the
   * one passed in.
   */
  default MethodInterceptor andThen(MethodInterceptor next) {
    return (invocation, chain) ->
        intercept(invocation, nextInvocation -> next.intercept(nextInvocation, chain));
  }

  /** Contract for delegation to the rest of the chain. */
  interface Chain {

    /** Sentinel end-of-chain that throws if invoked; the framework provides a real terminal. */
    Chain DEFAULT =
        invocation -> {
          throw new UnsupportedOperationException("end of MethodInterceptor chain");
        };

    /**
     * Delegate to the rest of the chain.
     *
     * @param invocation invocation context (typically the same instance received by {@link
     *     #intercept(Invocation, Chain)})
     * @return value produced by the downstream chain
     */
    Object next(Invocation invocation) throws Throwable;
  }
}
