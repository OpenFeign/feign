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
package feign.jaxrs;

import feign.Contract;
import feign.MethodMetadata;
import feign.Request;
import javax.ws.rs.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import static feign.Util.checkState;
import static feign.Util.emptyToNull;
import static feign.Util.removeValues;

/**
 * Please refer to the <a href="https://github.com/Netflix/feign/tree/master/feign-jaxrs">Feign
 * JAX-RS README</a>.
 */
public class JAXRSContract extends Contract.BaseContract {

  static final String ACCEPT = "Accept";
  static final String CONTENT_TYPE = "Content-Type";

  // Protected so unittest can call us
  // XXX: Should parseAndValidateMetadata(Class, Method) be public instead? The old deprecated
  // parseAndValidateMetadata(Method) was public..
  @Override
  protected MethodMetadata parseAndValidateMetadata(Class<?> targetType, Method method) {
    return super.parseAndValidateMetadata(targetType, method);
  }

  @Override
  protected void processAnnotationOnClass(MethodMetadata data, Class<?> clz) {
    Path path = clz.getAnnotation(Path.class);
    if (path != null && !path.value().isEmpty()) {
      String pathValue = path.value();
      if (!pathValue.startsWith("/")) {
        pathValue = "/" + pathValue;
      }
      if (pathValue.endsWith("/")) {
        // Strip off any trailing slashes, since the template has already had slashes appropriately
        // added
        pathValue = pathValue.substring(0, pathValue.length() - 1);
      }
      // jax-rs allows whitespace around the param name, as well as an optional regex. The contract
      // should
      // strip these out appropriately.
      pathValue = pathValue.replaceAll("\\{\\s*(.+?)\\s*(:.+?)?\\}", "\\{$1\\}");
      data.template().uri(pathValue);
    }
    Consumes consumes = clz.getAnnotation(Consumes.class);
    if (consumes != null) {
      handleConsumesAnnotation(data, consumes, clz.getName());
    }
    Produces produces = clz.getAnnotation(Produces.class);
    if (produces != null) {
      handleProducesAnnotation(data, produces, clz.getName());
    }
  }

  @Override
  protected void processAnnotationOnMethod(MethodMetadata data,
                                           Annotation methodAnnotation,
                                           Method method) {
    Class<? extends Annotation> annotationType = methodAnnotation.annotationType();
    HttpMethod http = annotationType.getAnnotation(HttpMethod.class);
    if (http != null) {
      checkState(data.template().method() == null,
          "Method %s contains multiple HTTP methods. Found: %s and %s", method.getName(),
          data.template().method(), http.value());
      data.template().method(Request.HttpMethod.valueOf(http.value()));
    } else if (annotationType == Path.class) {
      String pathValue = emptyToNull(Path.class.cast(methodAnnotation).value());
      if (pathValue == null) {
        return;
      }
      String methodAnnotationValue = Path.class.cast(methodAnnotation).value();
      if (!methodAnnotationValue.startsWith("/") && !data.template().url().endsWith("/")) {
        methodAnnotationValue = "/" + methodAnnotationValue;
      }
      // jax-rs allows whitespace around the param name, as well as an optional regex. The contract
      // should
      // strip these out appropriately.
      methodAnnotationValue =
          methodAnnotationValue.replaceAll("\\{\\s*(.+?)\\s*(:.+?)?\\}", "\\{$1\\}");
      data.template().uri(methodAnnotationValue, true);
    } else if (annotationType == Produces.class) {
      handleProducesAnnotation(data, (Produces) methodAnnotation, "method " + method.getName());
    } else if (annotationType == Consumes.class) {
      handleConsumesAnnotation(data, (Consumes) methodAnnotation, "method " + method.getName());
    }
  }

  private void handleProducesAnnotation(MethodMetadata data, Produces produces, String name) {
    String[] serverProduces =
        removeValues(produces.value(), (mediaType) -> emptyToNull(mediaType) == null, String.class);
    checkState(serverProduces.length > 0, "Produces.value() was empty on %s", name);
    data.template().header(ACCEPT, Collections.emptyList()); // remove any previous produces
    data.template().header(ACCEPT, serverProduces);
  }

  private void handleConsumesAnnotation(MethodMetadata data, Consumes consumes, String name) {
    String[] serverConsumes =
        removeValues(consumes.value(), (mediaType) -> emptyToNull(mediaType) == null, String.class);
    checkState(serverConsumes.length > 0, "Consumes.value() was empty on %s", name);
    data.template().header(CONTENT_TYPE, Collections.emptyList()); // remove any previous consumes
    data.template().header(CONTENT_TYPE, serverConsumes[0]);
  }

  /**
   * Allows derived contracts to specify unsupported jax-rs parameter annotations which should be
   * ignored. Required for JAX-RS 2 compatibility.
   */
  protected boolean isUnsupportedHttpParameterAnnotation(Annotation parameterAnnotation) {
    return false;
  }

  @Override
  protected boolean processAnnotationsOnParameter(MethodMetadata data,
                                                  Annotation[] annotations,
                                                  int paramIndex) {
    boolean isHttpParam = false;
    for (Annotation parameterAnnotation : annotations) {
      Class<? extends Annotation> annotationType = parameterAnnotation.annotationType();
      // masc20180327. parameter with unsupported jax-rs annotations should not be passed as body
      // params.
      // this will prevent interfaces from becoming unusable entirely due to single (unsupported)
      // endpoints.
      // https://github.com/OpenFeign/feign/issues/669
      if (this.isUnsupportedHttpParameterAnnotation(parameterAnnotation)) {
        isHttpParam = true;
      } else if (annotationType == PathParam.class) {
        String name = PathParam.class.cast(parameterAnnotation).value();
        checkState(emptyToNull(name) != null, "PathParam.value() was empty on parameter %s",
            paramIndex);
        nameParam(data, name, paramIndex);
        isHttpParam = true;
      } else if (annotationType == QueryParam.class) {
        String name = QueryParam.class.cast(parameterAnnotation).value();
        checkState(emptyToNull(name) != null, "QueryParam.value() was empty on parameter %s",
            paramIndex);
        Collection<String> query = addTemplatedParam(data.template().queries().get(name), name);
        data.template().query(name, query);
        nameParam(data, name, paramIndex);
        isHttpParam = true;
      } else if (annotationType == HeaderParam.class) {
        String name = HeaderParam.class.cast(parameterAnnotation).value();
        checkState(emptyToNull(name) != null, "HeaderParam.value() was empty on parameter %s",
            paramIndex);
        Collection<String> header = addTemplatedParam(data.template().headers().get(name), name);
        data.template().header(name, header);
        nameParam(data, name, paramIndex);
        isHttpParam = true;
      } else if (annotationType == FormParam.class) {
        String name = FormParam.class.cast(parameterAnnotation).value();
        checkState(emptyToNull(name) != null, "FormParam.value() was empty on parameter %s",
            paramIndex);
        data.formParams().add(name);
        nameParam(data, name, paramIndex);
        isHttpParam = true;
      }
    }
    return isHttpParam;
  }

  // Not using override as the super-type's method is deprecated and will be removed.
  protected Collection<String> addTemplatedParam(Collection<String> possiblyNull, String name) {
    if (possiblyNull == null) {
      possiblyNull = new ArrayList<String>();
    }
    possiblyNull.add(String.format("{%s}", name));
    return possiblyNull;
  }
}
