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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

/**
 * An implementation of {@link ResponseInterceptor} the returns the value of the location header
 * when appropriate.
 */
public class RedirectionInterceptor implements ResponseInterceptor {
  @Override
  public Object intercept(InvocationContext invocationContext, Chain chain) throws Exception {
    Response response = invocationContext.response();
    int status = response.status();
    Object returnValue = null;
    if (300 <= status && status < 400 && response.headers().containsKey("Location")) {
      Type returnType = rawType(invocationContext.returnType());
      Collection<String> locations = response.headers().get("Location");
      if (Collection.class.equals(returnType)) {
        returnValue = locations;
      } else if (String.class.equals(returnType)) {
        if (locations.isEmpty()) {
          returnValue = "";
        } else {
          returnValue = locations.stream().findFirst().orElse("");
        }
      }
    }
    if (returnValue == null) {
      return chain.next(invocationContext);
    } else {
      response.close();
      return returnValue;
    }
  }

  private Type rawType(Type type) {
    return type instanceof ParameterizedType ? ((ParameterizedType) type).getRawType() : type;
  }
}
