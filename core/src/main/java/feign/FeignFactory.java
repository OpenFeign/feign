/**
 * Copyright 2012-2018 The Feign Authors
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

public interface FeignFactory {

  Feign create(ReflectiveFeign.ParseHandlersByName targetToHandlersByName,
               InvocationHandlerFactory factory,
               QueryMapEncoder queryMapEncoder);

  static class Factory implements FeignFactory {

    @Override
    public Feign create(ReflectiveFeign.ParseHandlersByName handlersByName,
                        InvocationHandlerFactory invocationHandlerFactory,
                        QueryMapEncoder queryMapEncoder) {
      return new ReflectiveFeign(handlersByName, invocationHandlerFactory, queryMapEncoder);
    }
  }
}
