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
 * Zero or One {@code ClientInterceptor} may be configured for purposes such as tracing - mutate
 * headers, execute the call, parse the response and close any previously created objects.
 */
public interface ClientInterceptor {

  ClientInterceptor DEFAULT = new ClientInterceptor() {
    @Override
    public void beforeExecute(ClientInvocationContext context) {}

    @Override
    public void afterExecute(ClientInvocationContext context,
                             Response response,
                             Throwable exception) {

    }
  };

  /**
   * Called before the request was made. Can be used to mutate the request and create objects that
   * later will be passed to {@link #afterExecute(ClientInvocationContext, Response, Throwable)}.
   *
   * @param clientInvocationContext information surrounding the request
   */
  void beforeExecute(ClientInvocationContext clientInvocationContext);

  /**
   *
   * @param clientInvocationContext information surrounding the request
   * @param response received undecoded response (can be null when an exception occurred)
   * @param exception exception that occurred while trying to send the request or receive the
   *        response (can be null when there was no exception)
   */
  void afterExecute(ClientInvocationContext clientInvocationContext,
                    Response response,
                    Throwable exception);

}
