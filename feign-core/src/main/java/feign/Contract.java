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

import static feign.Util.ACCEPT;
import static feign.Util.CONTENT_TYPE;
import static feign.Util.checkState;
import static feign.Util.join;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

/** Defines what annotations and values are valid on interfaces. */
public final class Contract {

  public static Set<MethodMetadata> parseAndValidatateMetadata(Class<?> declaring) {
    Set<MethodMetadata> metadata = new LinkedHashSet<MethodMetadata>();
    for (Method method : declaring.getDeclaredMethods()) {
      if (method.getDeclaringClass() == Object.class) continue;
      metadata.add(parseAndValidatateMetadata(method));
    }
    return metadata;
  }

  public static MethodMetadata parseAndValidatateMetadata(Method method) {
    MethodMetadata data = new MethodMetadata();
    data.returnType(method.getGenericReturnType());
    data.configKey(Feign.configKey(method));

    for (Annotation methodAnnotation : method.getAnnotations()) {
      Class<? extends Annotation> annotationType = methodAnnotation.annotationType();
      HttpMethod http = annotationType.getAnnotation(HttpMethod.class);
      if (http != null) {
        checkState(
            data.template().method() == null,
            "Method %s contains multiple HTTP methods. Found: %s and %s",
            method.getName(),
            data.template().method(),
            http.value());
        data.template().method(http.value());
      } else if (annotationType == RequestTemplate.Body.class) {
        String body = RequestTemplate.Body.class.cast(methodAnnotation).value();
        if (body.indexOf('{') == -1) {
          data.template().body(body);
        } else {
          data.template().bodyTemplate(body);
        }
      } else if (annotationType == Path.class) {
        data.template().append(Path.class.cast(methodAnnotation).value());
      } else if (annotationType == Produces.class) {
        data.template().header(CONTENT_TYPE, join(',', ((Produces) methodAnnotation).value()));
      } else if (annotationType == Consumes.class) {
        data.template().header(ACCEPT, join(',', ((Consumes) methodAnnotation).value()));
      }
    }
    checkState(
        data.template().method() != null,
        "Method %s not annotated with HTTP method type (ex. GET, POST)",
        method.getName());
    Class<?>[] parameterTypes = method.getParameterTypes();

    Annotation[][] parameterAnnotationArrays = method.getParameterAnnotations();
    int count = parameterAnnotationArrays.length;
    for (int i = 0; i < count; i++) {
      boolean hasHttpAnnotation = false;

      Class<?> parameterType = parameterTypes[i];
      Annotation[] parameterAnnotations = parameterAnnotationArrays[i];
      if (parameterAnnotations != null) {
        for (Annotation parameterAnnotation : parameterAnnotations) {
          Class<? extends Annotation> annotationType = parameterAnnotation.annotationType();
          if (annotationType == PathParam.class) {
            indexName(data, i, PathParam.class.cast(parameterAnnotation).value());
            hasHttpAnnotation = true;
          } else if (annotationType == QueryParam.class) {
            String name = QueryParam.class.cast(parameterAnnotation).value();
            Collection<String> query = addTemplatedParam(data.template().queries().get(name), name);
            data.template().query(name, query);
            indexName(data, i, name);
            hasHttpAnnotation = true;
          } else if (annotationType == HeaderParam.class) {
            String name = HeaderParam.class.cast(parameterAnnotation).value();
            Collection<String> header =
                addTemplatedParam(data.template().headers().get(name), name);
            data.template().header(name, header);
            indexName(data, i, name);
            hasHttpAnnotation = true;
          } else if (annotationType == FormParam.class) {
            String form = FormParam.class.cast(parameterAnnotation).value();
            data.formParams().add(form);
            indexName(data, i, form);
            hasHttpAnnotation = true;
          }
        }
      }

      if (parameterType == URI.class) {
        data.urlIndex(i);
      } else if (!hasHttpAnnotation) {
        checkState(
            data.formParams().isEmpty(),
            "Body parameters cannot be used with @FormParam parameters.");
        checkState(data.bodyIndex() == null, "Method has too many Body parameters: %s", method);
        data.bodyIndex(i);
      }
    }
    return data;
  }

  private static Collection<String> addTemplatedParam(
      Collection<String> possiblyNull, String name) {
    if (possiblyNull == null) possiblyNull = new ArrayList<String>();
    possiblyNull.add(String.format("{%s}", name));
    return possiblyNull;
  }

  private static void indexName(MethodMetadata data, int i, String name) {
    Collection<String> names =
        data.indexToName().containsKey(i) ? data.indexToName().get(i) : new ArrayList<String>();
    names.add(name);
    data.indexToName().put(i, names);
  }
}
