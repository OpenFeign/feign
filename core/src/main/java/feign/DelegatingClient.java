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

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A class that combines both sync and async clients and is capable of executing requests for both
 */
public abstract class DelegatingClient<C> implements Client, AsyncClient<C> {

  private final Client delegate;
  private final AsyncClient<C> asyncDelegate;

  public DelegatingClient(Client delegate) {
    this.delegate = delegate;
    this.asyncDelegate = cast(delegate, AsyncClient.class);
  }

  public DelegatingClient(AsyncClient<C> asyncDelegate) {
    this.delegate = cast(asyncDelegate, Client.class);
    this.asyncDelegate = asyncDelegate;
  }

  private static <T> T cast(Object arg, Class<T> clazz) {
    if (arg.getClass().isAssignableFrom(clazz)) {
      return clazz.cast(arg);
    }
    throw new IllegalArgumentException(arg + " is not a type of " + clazz);
  }

  @Override
  public Response execute(Request request, Request.Options options) throws IOException {
    if (this.delegate == null) {
      throw new IllegalStateException("The client does not have a sync delegate!");
    }
    return this.delegate.execute(request, options);
  }

  @Override
  public CompletableFuture<Response> execute(Request request,
                                             Request.Options options,
                                             Optional<C> requestContext) {
    if (this.asyncDelegate == null) {
      throw new IllegalStateException("The client does not have an async delegate!");
    }
    return this.asyncDelegate.execute(request, options, requestContext);
  }
}
