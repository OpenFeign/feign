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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

  abstract class BaseContract implements Contract {

    @Override
    public List<MethodMetadata> parseAndValidatateMetadata(Class<?> declaring) {
      List<MethodMetadata> metadata = new ArrayList<MethodMetadata>();
      for (Method method : declaring.getDeclaredMethods()) {
        if (method.getDeclaringClass() == Object.class) {
          continue;
        }
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
      checkState(data.template().method() != null,
                 "Method %s not annotated with HTTP method type (ex. GET, POST)",
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
          checkState(data.formParams().isEmpty(),
                     "Body parameters cannot be used with form parameters.");
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
    protected abstract void processAnnotationOnMethod(MethodMetadata data, Annotation annotation,
                                                      Method method);

    /**
     * @param data        metadata collected so far relating to the current java method.
     * @param annotations annotations present on the current parameter annotation.
     * @param paramIndex  if you find a name in {@code annotations}, call {@link
     *                    #nameParam(MethodMetadata, String, int)} with this as the last parameter.
     * @return true if you called {@link #nameParam(MethodMetadata, String, int)} after finding an
     * http-relevant annotation.
     */
    protected abstract boolean processAnnotationsOnParameter(MethodMetadata data,
                                                             Annotation[] annotations,
                                                             int paramIndex);


    protected Collection<String> addTemplatedParam(Collection<String> possiblyNull, String name) {
      if (possiblyNull == null) {
        possiblyNull = new ArrayList<String>();
      }
      possiblyNull.add(String.format("{%s}", name));
      return possiblyNull;
    }

    /**
     * links a parameter name to its index in the method signature.
     */
    protected void nameParam(MethodMetadata data, String name, int i) {
      Collection<String>
          names =
          data.indexToName().containsKey(i) ? data.indexToName().get(i) : new ArrayList<String>();
      names.add(name);
      data.indexToName().put(i, names);
    }
  }

  class Default extends BaseContract {

    @Override
    public MethodMetadata parseAndValidatateMetadata(Method method) {
      MethodMetadata data = super.parseAndValidatateMetadata(method);
      if (method.getDeclaringClass().isAnnotationPresent(Headers.class)) {
        String[] headersOnType = method.getDeclaringClass().getAnnotation(Headers.class).value();
        checkState(headersOnType.length > 0, "Headers annotation was empty on type %s.",
                   method.getDeclaringClass().getName());
        Map<String, Collection<String>> headers = toMap(headersOnType);
        headers.putAll(data.template().headers());
        data.template().headers(null); // to clear
        data.template().headers(headers);
      }
      return data;
    }

    @Override
    protected void processAnnotationOnMethod(MethodMetadata data, Annotation methodAnnotation,
                                             Method method) {
      Class<? extends Annotation> annotationType = methodAnnotation.annotationType();
      if (annotationType == RequestLine.class) {
        String requestLine = RequestLine.class.cast(methodAnnotation).value();
        checkState(emptyToNull(requestLine) != null,
                   "RequestLine annotation was empty on method %s.", method.getName());
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
          data.template().append(
              requestLine.substring(requestLine.indexOf(' ') + 1, requestLine.lastIndexOf(' ')));
        }
        
        data.template().decodeSlash(RequestLine.class.cast(methodAnnotation).decodeSlash());
        
      } else if (annotationType == Body.class) {
        String body = Body.class.cast(methodAnnotation).value();
        checkState(emptyToNull(body) != null, "Body annotation was empty on method %s.",
                   method.getName());
        if (body.indexOf('{') == -1) {
          data.template().body(body);
        } else {
          data.template().bodyTemplate(body);
        }
      } else if (annotationType == Headers.class) {
        String[] headersOnMethod = Headers.class.cast(methodAnnotation).value();
        checkState(headersOnMethod.length > 0, "Headers annotation was empty on method %s.",
                   method.getName());
        data.template().headers(toMap(headersOnMethod));
      }
    }

    @Override
    protected boolean processAnnotationsOnParameter(MethodMetadata data, Annotation[] annotations,
                                                    int paramIndex) {
      boolean isHttpAnnotation = false;
      for (Annotation annotation : annotations) {
        Class<? extends Annotation> annotationType = annotation.annotationType();
        if (annotationType == Param.class) {
          String name = ((Param) annotation).value();
          checkState(emptyToNull(name) != null, "Param annotation was empty on param %s.",
                     paramIndex);
          nameParam(data, name, paramIndex);
          if (annotationType == Param.class) {
            Class<? extends Param.Expander> expander = ((Param) annotation).expander();
            if (expander != Param.ToStringExpander.class) {
              data.indexToExpanderClass().put(paramIndex, expander);
            }
          }
          isHttpAnnotation = true;
          String varName = '{' + name + '}';
          if (data.template().url().indexOf(varName) == -1 &&
              !searchMapValues(data.template().queries(), varName) &&
              !searchMapValues(data.template().headers(), varName)) {
            data.formParams().add(name);
          }
        }
      }
      return isHttpAnnotation;
    }

    private static <K, V> boolean searchMapValues(Map<K, Collection<V>> map, V search) {
      Collection<Collection<V>> values = map.values();
      if (values == null) {
        return false;
      }

      for (Collection<V> entry : values) {
        if (entry.contains(search)) {
          return true;
        }
      }

      return false;
    }

    private static Map<String, Collection<String>> toMap(String[] input) {
      Map<String, Collection<String>>
          result =
          new LinkedHashMap<String, Collection<String>>(input.length);
      for (String header : input) {
        int colon = header.indexOf(':');
        String name = header.substring(0, colon);
        if (!result.containsKey(name)) {
          result.put(name, new ArrayList<String>(1));
        }
        result.get(name).add(header.substring(colon + 2));
      }
      return result;
    }
  }
}
