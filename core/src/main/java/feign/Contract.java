/*
 * Copyright 2013 Netflix, Inc.
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

import javax.inject.Named;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static feign.Util.checkState;
import static feign.Util.emptyToNull;

/**
 * Defines what annotations and values are valid on interfaces.
 */
public interface Contract {

  /**
   * Called to parse the methods in the class that are linked to HTTP requests.
   */
  List<MethodMetadata> parseAndValidatateMetadata(Class<?> declaring);

  public static abstract class BaseContract implements Contract {

    @Override public List<MethodMetadata> parseAndValidatateMetadata(Class<?> declaring) {
      List<MethodMetadata> metadata = new ArrayList<MethodMetadata>();
      for (Method method : declaring.getDeclaredMethods()) {
        if (method.getDeclaringClass() == Object.class)
          continue;
        metadata.add(parseAndValidatateMetadata(method));
      }
      return metadata;
    }

    /**
     * Called indirectly by {@link #parseAndValidatateMetadata(Class)}.
     */
    public MethodMetadata parseAndValidatateMetadata(Method method) {
      MethodMetadata data = new MethodMetadata();
      data.returnType(method.getGenericReturnType());
      data.configKey(Feign.configKey(method));

      for (Annotation methodAnnotation : method.getAnnotations()) {
        processAnnotationOnMethod(data, methodAnnotation, method);
      }
      checkState(data.template().method() != null, "Method %s not annotated with HTTP method type (ex. GET, POST)",
          method.getName());
      Class<?>[] parameterTypes = method.getParameterTypes();

      Annotation[][] parameterAnnotations = method.getParameterAnnotations();
      int count = parameterAnnotations.length;
      for (int i = 0; i < count; i++) {
        boolean isHttpAnnotation = false;
        if (parameterAnnotations[i] != null) {
          isHttpAnnotation = processAnnotationsOnParameter(data, parameterAnnotations[i], i);
        }
        if (parameterTypes[i] == URI.class) {
          data.urlIndex(i);
        } else if (!isHttpAnnotation) {
          checkState(data.formParams().isEmpty(), "Body parameters cannot be used with form parameters.");
          checkState(data.bodyIndex() == null, "Method has too many Body parameters: %s", method);
          data.bodyIndex(i);
          data.bodyType(method.getGenericParameterTypes()[i]);
        }
      }
      return data;
    }

    /**
     * @param data       metadata collected so far relating to the current java method.
     * @param annotation annotations present on the current method annotation.
     * @param method     method currently being processed.
     */
    protected abstract void processAnnotationOnMethod(MethodMetadata data, Annotation annotation, Method method);

    /**
     * @param data        metadata collected so far relating to the current java method.
     * @param annotations annotations present on the current parameter annotation.
     * @param paramIndex  if you find a name in {@code annotations}, call {@link #nameParam(MethodMetadata, String,
     *                    int)} with this as the last parameter.
     * @return true if you called {@link #nameParam(MethodMetadata, String, int)} after finding an http-relevant
     *         annotation.
     */
    protected abstract boolean processAnnotationsOnParameter(MethodMetadata data, Annotation[] annotations, int paramIndex);


    protected Collection<String> addTemplatedParam(Collection<String> possiblyNull, String name) {
      if (possiblyNull == null)
        possiblyNull = new ArrayList<String>();
      possiblyNull.add(String.format("{%s}", name));
      return possiblyNull;
    }

    /**
     * links a parameter name to its index in the method signature.
     */
    protected void nameParam(MethodMetadata data, String name, int i) {
      Collection<String> names = data.indexToName().containsKey(i) ? data.indexToName().get(i) : new ArrayList<String>();
      names.add(name);
      data.indexToName().put(i, names);
    }
  }

  static class Default extends BaseContract {

    @Override
    protected void processAnnotationOnMethod(MethodMetadata data, Annotation methodAnnotation, Method method) {
      Class<? extends Annotation> annotationType = methodAnnotation.annotationType();
      if (annotationType == RequestLine.class) {
        String requestLine = RequestLine.class.cast(methodAnnotation).value();
        checkState(emptyToNull(requestLine) != null, "RequestLine annotation was empty on method %s.", method.getName());
        if (requestLine.indexOf(' ') == -1) {
          data.template().method(requestLine);
          return;
        }
        data.template().method(requestLine.substring(0, requestLine.indexOf(' ')));
        if (requestLine.indexOf(' ') == requestLine.lastIndexOf(' ')) {
          // no HTTP version is ok
          data.template().append(requestLine.substring(requestLine.indexOf(' ') + 1));
        } else {
          // skip HTTP version
          data.template().append(requestLine.substring(requestLine.indexOf(' ') + 1, requestLine.lastIndexOf(' ')));
        }
      } else if (annotationType == Body.class) {
        String body = Body.class.cast(methodAnnotation).value();
        checkState(emptyToNull(body) != null, "Body annotation was empty on method %s.", method.getName());
        if (body.indexOf('{') == -1) {
          data.template().body(body);
        } else {
          data.template().bodyTemplate(body);
        }
      } else if (annotationType == Headers.class) {
        String[] headersToParse = Headers.class.cast(methodAnnotation).value();
        checkState(headersToParse.length > 0, "Headers annotation was empty on method %s.", method.getName());
        for (String header : headersToParse) {
          int colon = header.indexOf(':');
          data.template().header(header.substring(0, colon), header.substring(colon + 2));
        }
      }
    }

    @Override
    protected boolean processAnnotationsOnParameter(MethodMetadata data, Annotation[] annotations, int paramIndex) {
      boolean isHttpAnnotation = false;
      for (Annotation parameterAnnotation : annotations) {
        Class<? extends Annotation> annotationType = parameterAnnotation.annotationType();
        if (annotationType == Named.class) {
          String name = Named.class.cast(parameterAnnotation).value();
          checkState(emptyToNull(name) != null, "Named annotation was empty on param %s.", paramIndex);
          nameParam(data, name, paramIndex);
          isHttpAnnotation = true;
          if (data.template().url().indexOf('{' + name + '}') == -1 && //
              !(data.template().queries().containsKey(name)
                  || data.template().headers().containsKey(name))) {
            data.formParams().add(name);
          }
        }
      }
      return isHttpAnnotation;
    }
  }
}
