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

import feign.Request;
import feign.Response;
import feign.Types;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import static feign.Util.checkState;

class ExceptionGenerator {

  private static final Response TEST_RESPONSE;

  static {
    Map<String, Collection<String>> testHeaders = new HashMap<String, Collection<String>>();
    testHeaders.put("TestHeader", Arrays.asList("header-value"));

    TEST_RESPONSE = Response.builder()
        .status(500)
        .body((Response.Body) null)
        .headers(testHeaders)
        .request(Request.create(Request.HttpMethod.GET, "http://test", testHeaders,
            Request.Body.empty(), null))
        .build();
  }

  private final Integer bodyIndex;
  private final Integer requestIndex;
  private final Integer headerMapIndex;
  private final Integer numOfParams;
  private final Type bodyType;
  private final Class<? extends Exception> exceptionType;
  private final Decoder bodyDecoder;

  ExceptionGenerator(Integer bodyIndex, Integer requestIndex, Integer headerMapIndex,
      Integer numOfParams, Type bodyType,
      Class<? extends Exception> exceptionType, Decoder bodyDecoder) {
    this.bodyIndex = bodyIndex;
    this.requestIndex = requestIndex;
    this.headerMapIndex = headerMapIndex;
    this.numOfParams = numOfParams;
    this.bodyType = bodyType;
    this.exceptionType = exceptionType;
    this.bodyDecoder = bodyDecoder;
  }


  Exception createException(Response response) throws InvocationTargetException,
      NoSuchMethodException, InstantiationException, IllegalAccessException {

    Class<?>[] paramClasses = new Class[numOfParams];
    Object[] paramValues = new Object[numOfParams];
    if (bodyIndex >= 0) {
      paramClasses[bodyIndex] = Types.getRawType(bodyType);
      paramValues[bodyIndex] = resolveBody(response);
    }
    if (requestIndex >= 0) {
      paramClasses[requestIndex] = Request.class;
      paramValues[requestIndex] = response.request();
    }
    if (headerMapIndex >= 0) {
      paramValues[headerMapIndex] = response.headers();
      paramClasses[headerMapIndex] = Map.class;
    }
    return exceptionType.getConstructor(paramClasses)
        .newInstance(paramValues);

  }

  Class<? extends Exception> getExceptionType() {
    return exceptionType;
  }

  private Object resolveBody(Response response) {
    if (bodyType instanceof Class<?> && ((Class<?>) bodyType).isInstance(response)) {
      return response;
    }
    try {
      return bodyDecoder.decode(response, bodyType);
    } catch (IOException e) {
      // How do we log this?
      return null;
    } catch (DecodeException e) {
      // How do we log this?
      return null;
    }
  }

  static class Builder {
    private Class<? extends Exception> exceptionType;
    private Decoder responseBodyDecoder;

    public Builder withExceptionType(Class<? extends Exception> exceptionType) {
      this.exceptionType = exceptionType;
      return this;
    }

    public Builder withResponseBodyDecoder(Decoder bodyDecoder) {
      this.responseBodyDecoder = bodyDecoder;
      return this;
    }

    public ExceptionGenerator build() {
      Constructor<? extends Exception> constructor = getConstructor(exceptionType);
      Type[] parameterTypes = constructor.getGenericParameterTypes();
      Annotation[][] parametersAnnotations = constructor.getParameterAnnotations();

      Integer bodyIndex = -1;
      Integer requestIndex = -1;
      Integer headerMapIndex = -1;
      Integer numOfParams = parameterTypes.length;
      Type bodyType = null;

      for (int i = 0; i < parameterTypes.length; i++) {
        Annotation[] paramAnnotations = parametersAnnotations[i];
        boolean foundAnnotation = false;
        for (Annotation annotation : paramAnnotations) {
          if (annotation.annotationType().equals(ResponseHeaders.class)) {
            checkState(headerMapIndex == -1,
                "Cannot have two parameters tagged with @ResponseHeaders");
            checkState(Types.getRawType(parameterTypes[i]).equals(Map.class),
                "Response Header map must be of type Map, but was %s", parameterTypes[i]);
            headerMapIndex = i;
            foundAnnotation = true;
            break;
          }
        }
        if (!foundAnnotation) {
          if (parameterTypes[i].equals(Request.class)) {
            checkState(requestIndex == -1,
                "Cannot have two parameters either without annotations or with object of type feign.Request");
            requestIndex = i;
          } else {
            checkState(bodyIndex == -1,
                "Cannot have two parameters either without annotations or with @ResponseBody annotation");
            bodyIndex = i;
            bodyType = parameterTypes[i];
          }
        }
      }

      ExceptionGenerator generator = new ExceptionGenerator(
          bodyIndex,
          requestIndex,
          headerMapIndex,
          numOfParams,
          bodyType,
          exceptionType,
          responseBodyDecoder);

      validateGeneratorCanBeUsedToGenerateExceptions(generator);
      return generator;
    }

    private void validateGeneratorCanBeUsedToGenerateExceptions(ExceptionGenerator generator) {
      try {
        generator.createException(TEST_RESPONSE);
      } catch (Exception e) {
        throw new IllegalStateException(
            "Cannot generate exception - check constructor parameter types (are headers Map<String,Collection<String>> or is something causing an exception on construction?)",
            e);
      }
    }

    private Constructor<? extends Exception> getConstructor(Class<? extends Exception> exceptionClass) {
      Constructor<? extends Exception> preferredConstructor = null;
      for (Constructor<?> constructor : exceptionClass.getConstructors()) {

        FeignExceptionConstructor exceptionConstructor =
            constructor.getAnnotation(FeignExceptionConstructor.class);
        if (exceptionConstructor == null) {
          continue;
        }
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        if (parameterTypes.length == 0) {
          continue;
        }
        if (preferredConstructor == null) {
          preferredConstructor = (Constructor<? extends Exception>) constructor;
        } else {
          throw new IllegalStateException(
              "Too many constructors marked with @FeignExceptionConstructor");
        }
      }

      if (preferredConstructor == null) {
        try {
          return exceptionClass.getConstructor();
        } catch (NoSuchMethodException e) {
          throw new IllegalStateException(
              "Cannot find any suitable constructor in class [" + exceptionClass.getName()
                  + "] - did you forget to mark one with @FeignExceptionConstructor or at least have a public default constructor?",
              e);
        }
      }
      return preferredConstructor;
    }
  }
}
