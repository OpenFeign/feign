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
package feign;

import static feign.Util.checkNotNull;

import java.lang.reflect.Method;

/**
 * Context for a single method invocation passed to {@link MethodInterceptor}s. Exposes the resolved
 * {@link RequestTemplate} (mutable until the request is sent), the contract's {@link
 * MethodMetadata}, the raw method arguments, and — after the chain has executed the HTTP call — the
 * resulting {@link Response}.
 */
@Experimental
public final class Invocation {

  private final Target<?> target;
  private final MethodMetadata methodMetadata;
  private final RequestTemplate requestTemplate;
  private final Object[] arguments;
  private volatile Response response;

  public Invocation(
      Target<?> target,
      MethodMetadata methodMetadata,
      RequestTemplate requestTemplate,
      Object[] arguments) {
    this.target = checkNotNull(target, "target");
    this.methodMetadata = checkNotNull(methodMetadata, "methodMetadata");
    this.requestTemplate = checkNotNull(requestTemplate, "requestTemplate");
    this.arguments = arguments;
  }

  public Target<?> target() {
    return target;
  }

  public MethodMetadata methodMetadata() {
    return methodMetadata;
  }

  /** Convenience accessor for the underlying reflective {@link Method}. */
  public Method method() {
    return methodMetadata.method();
  }

  /** The mutable, contract-resolved request template. Mutations are visible downstream. */
  public RequestTemplate requestTemplate() {
    return requestTemplate;
  }

  /** Raw arguments passed to the proxied method. May be {@code null} for no-arg methods. */
  public Object[] arguments() {
    return arguments;
  }

  /**
   * The HTTP {@link Response} captured after the framework's terminal chain step. Returns {@code
   * null} when the chain has not yet executed the HTTP call, or when the call failed before a
   * response was received.
   */
  public Response response() {
    return response;
  }

  /** Package-private setter used by the framework's terminal chain step. */
  void response(Response response) {
    this.response = response;
  }
}
