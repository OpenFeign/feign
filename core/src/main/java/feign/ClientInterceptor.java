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

import java.util.Iterator;

/**
 * Zero or One {@code ClientInterceptor} may be configured for purposes such as tracing - mutate
 * headers, execute the call, parse the response and close any previously created objects.
 */
public interface ClientInterceptor {

  /**
   * Returns the default instance of {@link ClientInterceptor}. We don't need to check if the
   * iterator has a next entry because the {@link SynchronousMethodHandler} will add 1 entry. That
   * means that in the default scenario an iterator to that entry will be passed here.
   */
  ClientInterceptor DEFAULT = (context, iterator) -> {
    ClientInterceptor next = iterator.next();
    return next.around(context, iterator);
  };

  /**
   * Allows to make an around instrumentation. Remember to call the next interceptor by calling
   * {@code ClientInterceptor interceptor = iterator.next(); return interceptor.around(context, iterator)}.
   *
   * @param context input context to send a request
   * @param iterator iterator to the next {@link ClientInterceptor}
   * @return response or an exception - remember to rethrow an exception if it occurrs
   * @throws FeignException exception while trying to send a request
   */
  Response around(ClientInvocationContext context, Iterator<ClientInterceptor> iterator)
      throws FeignException;

}
