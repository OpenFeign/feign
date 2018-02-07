/**
 * Copyright 2013 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.codec;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import feign.RequestTemplate;

/**
 * Use an Object as source for query parameters.
 * Add non null values of properties as query parameters using the property name as the query parameter name
 */
public class ObjectToQueryParamEncoder implements Encoder {

  static String toParamTemplate(String queryParamName) {
    return "{" + queryParamName + "}";
  }

  private String decapitalize(String name) {
    if (name == null || name.length() == 0) {
      return name;
    }
    char chars[] = name.toCharArray();
    chars[0] = Character.toLowerCase(chars[0]);
    return new String(chars);
  }

  @Override
  public void encode(Object queryParametersObject, Type bodyType, RequestTemplate template) throws EncodeException {
    if (queryParametersObject != null) {
      // Store query parameters values
      Map<String, Object> queryParams = new HashMap<String, Object>();
      try {
        for (Method method : queryParametersObject.getClass().getMethods()) {
          analyseMethod(queryParametersObject, method, template, queryParams);
        }
      }
      catch (InvocationTargetException e) {
        throw new EncodeException("Could not encode query parameters from object", e);
      }
      catch (IllegalAccessException e) {
        throw new EncodeException("Could not encode query parameters from object", e);
      }
      // resolve newly added parameters
      if (!queryParams.isEmpty()) {
        template.resolve(queryParams);
      }
    }
  }

  /**
   * Add non null values of properties as query parameters using the property name as the query parameter name
   */
  private void analyseMethod(Object queryParametersObject,
                             Method method,
                             RequestTemplate template,
                             Map<String, Object> queryParams) throws IllegalAccessException, InvocationTargetException {
    // filter method to "getter" like but not those from Object (ex: getClass())
    if (!Object.class.equals(method.getDeclaringClass()) && method.getParameterTypes() != null && method.getParameterTypes().length == 0) {
      String queryParamName = findPropertyName(method);
      if (queryParamName != null) {
        Object queryParamValue = method.invoke(queryParametersObject);

        // Add discovered parameter if present
        if (queryParamName != null && queryParamValue != null) {
          queryParams.put(queryParamName, queryParamValue);
          template.query(queryParamName, toParamTemplate(queryParamName));
        }
      }
    }
  }

  /**
   * @param method
   * @return property name associated to a getter or null if the method is not a getter
   */
  private String findPropertyName(Method method) {
    String queryParamName = null;
    if (method.getName().startsWith("get")) {
      queryParamName = decapitalize(method.getName().substring(3));
    }
    else if (method.getName().startsWith("is")) {
      queryParamName = decapitalize(method.getName().substring(2));
    }
    return queryParamName;
  }
}
