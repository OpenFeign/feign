/**
 * Copyright 2012-2021 The Feign Authors
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
package feign.defaultmethodhandler;

import feign.InvocationHandlerFactory;
import feign.InvocationHandlerFactory.MethodHandler;
import org.jvnet.animal_sniffer.IgnoreJRERequirement;

/**
 * Handles default methods by directly invoking the default method code on the interface. The bindTo
 * method must be called on the result before invoke is called.
 */
public interface DefaultMethodHandler extends MethodHandler {


  /**
   * Bind this handler to a proxy object. After bound, DefaultMethodHandler#invoke will act as if it
   * was called on the proxy object. Must be called once and only once for a given instance of
   * DefaultMethodHandler
   */
  public void bindTo(Object proxy);

}
