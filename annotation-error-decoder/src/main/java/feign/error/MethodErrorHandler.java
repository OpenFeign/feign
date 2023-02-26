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
package feign.error;

import feign.Response;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

class MethodErrorHandler {

  private final Map<Integer, ExceptionGenerator> methodLevelExceptionsByCode;
  private final Map<Integer, ExceptionGenerator> classLevelExceptionsByCode;
  private final ExceptionGenerator defaultException;

  MethodErrorHandler(Map<Integer, ExceptionGenerator> methodLevelExceptionsByCode,
      Map<Integer, ExceptionGenerator> classLevelExceptionsByCode,
      ExceptionGenerator defaultException) {
    this.methodLevelExceptionsByCode = methodLevelExceptionsByCode;
    this.classLevelExceptionsByCode = classLevelExceptionsByCode;
    this.defaultException = defaultException;
  }


  public Exception decode(Response response) {
    ExceptionGenerator constructorDefinition = getConstructorDefinition(response);
    return createException(constructorDefinition, response);
  }

  private ExceptionGenerator getConstructorDefinition(Response response) {
    if (methodLevelExceptionsByCode.containsKey(response.status())) {
      return methodLevelExceptionsByCode.get(response.status());
    }
    if (classLevelExceptionsByCode.containsKey(response.status())) {
      return classLevelExceptionsByCode.get(response.status());
    }
    return defaultException;
  }

  protected Exception createException(ExceptionGenerator constructorDefinition, Response response) {
    try {
      return constructorDefinition.createException(response);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Cannot access constructor", e);
    } catch (InstantiationException e) {
      throw new IllegalStateException("Cannot instantiate exception with constructor", e);
    } catch (InvocationTargetException e) {
      throw new IllegalStateException("Cannot invoke constructor", e);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("Constructor does not exist", e);
    }
  }
}
