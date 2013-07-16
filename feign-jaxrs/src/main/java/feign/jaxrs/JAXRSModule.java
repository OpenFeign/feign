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
package feign.jaxrs;

import dagger.Provides;
import feign.Body;
import feign.Contract;
import feign.MethodMetadata;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;

import static feign.Util.checkState;

@dagger.Module(library = true, overrides = true)
public final class JAXRSModule {
  static final String ACCEPT = "Accept";
  static final String CONTENT_TYPE = "Content-Type";

  @Provides Contract provideContract() {
    return new JAXRSContract();
  }

  public static final class JAXRSContract extends Contract {

    @Override
    protected void processAnnotationOnMethod(MethodMetadata data, Annotation methodAnnotation, Method method) {
      Class<? extends Annotation> annotationType = methodAnnotation.annotationType();
      HttpMethod http = annotationType.getAnnotation(HttpMethod.class);
      if (http != null) {
        checkState(data.template().method() == null,
            "Method %s contains multiple HTTP methods. Found: %s and %s", method.getName(), data.template()
            .method(), http.value());
        data.template().method(http.value());
      } else if (annotationType == Body.class) {
        String body = Body.class.cast(methodAnnotation).value();
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

    @Override
    protected boolean processAnnotationsOnParameter(MethodMetadata data, Annotation[] annotations, int paramIndex) {
      boolean isHttpParam = false;
      for (Annotation parameterAnnotation : annotations) {
        Class<? extends Annotation> annotationType = parameterAnnotation.annotationType();
        if (annotationType == PathParam.class) {
          String name = PathParam.class.cast(parameterAnnotation).value();
          nameParam(data, name, paramIndex);
          isHttpParam = true;
        } else if (annotationType == QueryParam.class) {
          String name = QueryParam.class.cast(parameterAnnotation).value();
          Collection<String> query = addTemplatedParam(data.template().queries().get(name), name);
          data.template().query(name, query);
          nameParam(data, name, paramIndex);
          isHttpParam = true;
        } else if (annotationType == HeaderParam.class) {
          String name = HeaderParam.class.cast(parameterAnnotation).value();
          Collection<String> header = addTemplatedParam(data.template().headers().get(name), name);
          data.template().header(name, header);
          nameParam(data, name, paramIndex);
          isHttpParam = true;
        } else if (annotationType == FormParam.class) {
          String name = FormParam.class.cast(parameterAnnotation).value();
          data.formParams().add(name);
          nameParam(data, name, paramIndex);
          isHttpParam = true;
        }
      }
      return isHttpParam;
    }
  }

  private static String join(char separator, String... parts) {
    if (parts == null || parts.length == 0)
      return "";
    StringBuilder to = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      to.append(parts[i]);
      if (i + 1 < parts.length) {
        to.append(separator);
      }
    }
    return to.toString();
  }
}
