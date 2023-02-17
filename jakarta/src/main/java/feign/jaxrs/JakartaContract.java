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
package feign.jaxrs;

import static feign.Util.checkState;
import static feign.Util.emptyToNull;
import static feign.Util.removeValues;
import feign.DeclarativeContract;
import feign.MethodMetadata;
import feign.Request;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;

public class JakartaContract extends DeclarativeContract {

  static final String ACCEPT = "Accept";
  static final String CONTENT_TYPE = "Content-Type";

  // Protected so unittest can call us
  // XXX: Should parseAndValidateMetadata(Class, Method) be public instead? The old deprecated
  // parseAndValidateMetadata(Method) was public..
  @Override
  protected MethodMetadata parseAndValidateMetadata(Class<?> targetType, Method method) {
    return super.parseAndValidateMetadata(targetType, method);
  }

  public JakartaContract() {
    super.registerClassAnnotation(Path.class, (path, data) -> {
      if (path != null && !path.value().isEmpty()) {
        String pathValue = path.value();
        if (!pathValue.startsWith("/")) {
          pathValue = "/" + pathValue;
        }
        if (pathValue.endsWith("/")) {
          // Strip off any trailing slashes, since the template has already had slashes
          // appropriately
          // added
          pathValue = pathValue.substring(0, pathValue.length() - 1);
        }
        // jax-rs allows whitespace around the param name, as well as an optional regex. The
        // contract
        // should
        // strip these out appropriately.
        pathValue = pathValue.replaceAll("\\{\\s*(.+?)\\s*(:.+?)?\\}", "\\{$1\\}");
        data.template().uri(pathValue);
      }
    });
    super.registerClassAnnotation(Consumes.class, this::handleConsumesAnnotation);
    super.registerClassAnnotation(Produces.class, this::handleProducesAnnotation);

    registerMethodAnnotation(methodAnnotation -> {
      final Class<? extends Annotation> annotationType = methodAnnotation.annotationType();
      final HttpMethod http = annotationType.getAnnotation(HttpMethod.class);
      return http != null;
    }, (methodAnnotation, data) -> {
      final Class<? extends Annotation> annotationType = methodAnnotation.annotationType();
      final HttpMethod http = annotationType.getAnnotation(HttpMethod.class);
      checkState(data.template().method() == null,
          "Method %s contains multiple HTTP methods. Found: %s and %s", data.configKey(),
          data.template().method(), http.value());
      data.template().method(Request.HttpMethod.valueOf(http.value()));
    });

    super.registerMethodAnnotation(Path.class, (path, data) -> {
      final String pathValue = emptyToNull(path.value());
      if (pathValue == null) {
        return;
      }
      String methodAnnotationValue = path.value();
      if (!methodAnnotationValue.startsWith("/") && !data.template().url().endsWith("/")) {
        methodAnnotationValue = "/" + methodAnnotationValue;
      }
      // jax-rs allows whitespace around the param name, as well as an optional regex. The contract
      // should
      // strip these out appropriately.
      methodAnnotationValue =
          methodAnnotationValue.replaceAll("\\{\\s*(.+?)\\s*(:.+?)?\\}", "\\{$1\\}");
      data.template().uri(methodAnnotationValue, true);
    });
    super.registerMethodAnnotation(Consumes.class, this::handleConsumesAnnotation);
    super.registerMethodAnnotation(Produces.class, this::handleProducesAnnotation);

    // parameter with unsupported jax-rs annotations should not be passed as body params.
    // this will prevent interfaces from becoming unusable entirely due to single (unsupported)
    // endpoints.
    // https://github.com/OpenFeign/feign/issues/669
    super.registerParameterAnnotation(Suspended.class, (ann, data, i) -> data.ignoreParamater(i));
    super.registerParameterAnnotation(Context.class, (ann, data, i) -> data.ignoreParamater(i));
    // trying to minimize the diff
    registerParamAnnotations();
  }

  private void handleProducesAnnotation(Produces produces, MethodMetadata data) {
    final String[] serverProduces =
        removeValues(produces.value(), mediaType -> emptyToNull(mediaType) == null, String.class);
    checkState(serverProduces.length > 0, "Produces.value() was empty on %s", data.configKey());
    data.template().header(ACCEPT, Collections.emptyList()); // remove any previous produces
    data.template().header(ACCEPT, serverProduces);
  }

  private void handleConsumesAnnotation(Consumes consumes, MethodMetadata data) {
    final String[] serverConsumes =
        removeValues(consumes.value(), mediaType -> emptyToNull(mediaType) == null, String.class);
    checkState(serverConsumes.length > 0, "Consumes.value() was empty on %s", data.configKey());
    data.template().header(CONTENT_TYPE, serverConsumes);
  }

  protected void registerParamAnnotations() {

    registerParameterAnnotation(PathParam.class, (param, data, paramIndex) -> {
      final String name = param.value();
      checkState(emptyToNull(name) != null, "PathParam.value() was empty on parameter %s",
          paramIndex);
      nameParam(data, name, paramIndex);
    });
    registerParameterAnnotation(QueryParam.class, (param, data, paramIndex) -> {
      final String name = param.value();
      checkState(emptyToNull(name) != null, "QueryParam.value() was empty on parameter %s",
          paramIndex);
      final String query = addTemplatedParam(name);
      data.template().query(name, query);
      nameParam(data, name, paramIndex);
    });
    registerParameterAnnotation(HeaderParam.class, (param, data, paramIndex) -> {
      final String name = param.value();
      checkState(emptyToNull(name) != null, "HeaderParam.value() was empty on parameter %s",
          paramIndex);
      final String header = addTemplatedParam(name);
      data.template().header(name, header);
      nameParam(data, name, paramIndex);
    });
    registerParameterAnnotation(FormParam.class, (param, data, paramIndex) -> {
      final String name = param.value();
      checkState(emptyToNull(name) != null, "FormParam.value() was empty on parameter %s",
          paramIndex);
      data.formParams().add(name);
      nameParam(data, name, paramIndex);
    });

    // Reflect over the Bean Param looking for supported parameter annotations
    registerParameterAnnotation(BeanParam.class, (param, data, paramIndex) -> {
      final Field[] aggregatedParams = data.method()
          .getParameters()[paramIndex]
              .getType()
              .getDeclaredFields();

      for (Field aggregatedParam : aggregatedParams) {

        if (aggregatedParam.isAnnotationPresent(PathParam.class)) {
          final String name = aggregatedParam.getAnnotation(PathParam.class).value();
          checkState(
              emptyToNull(name) != null,
              "BeanParam parameter %s contains PathParam with empty .value() on field %s",
              paramIndex,
              aggregatedParam.getName());
          nameParam(data, name, paramIndex);
        }

        if (aggregatedParam.isAnnotationPresent(QueryParam.class)) {
          final String name = aggregatedParam.getAnnotation(QueryParam.class).value();
          checkState(
              emptyToNull(name) != null,
              "BeanParam parameter %s contains QueryParam with empty .value() on field %s",
              paramIndex,
              aggregatedParam.getName());
          final String query = addTemplatedParam(name);
          data.template().query(name, query);
          nameParam(data, name, paramIndex);
        }

        if (aggregatedParam.isAnnotationPresent(HeaderParam.class)) {
          final String name = aggregatedParam.getAnnotation(HeaderParam.class).value();
          checkState(
              emptyToNull(name) != null,
              "BeanParam parameter %s contains HeaderParam with empty .value() on field %s",
              paramIndex,
              aggregatedParam.getName());
          final String header = addTemplatedParam(name);
          data.template().header(name, header);
          nameParam(data, name, paramIndex);
        }

        if (aggregatedParam.isAnnotationPresent(FormParam.class)) {
          final String name = aggregatedParam.getAnnotation(FormParam.class).value();
          checkState(
              emptyToNull(name) != null,
              "BeanParam parameter %s contains FormParam with empty .value() on field %s",
              paramIndex,
              aggregatedParam.getName());
          data.formParams().add(name);
          nameParam(data, name, paramIndex);
        }
      }
    });

  }

  // Not using override as the super-type's method is deprecated and will be removed.
  private String addTemplatedParam(String name) {
    return String.format("{%s}", name);
  }
}
