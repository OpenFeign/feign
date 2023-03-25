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
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import feign.AlwaysEncodeBodyContract;
import feign.MethodMetadata;
import feign.Param.Expander;
import feign.Request;
import feign.jaxrs.AbstractParameterValidator.DefaultParameterExpander;
import feign.utils.JdkVersionResolver;
import feign.utils.RecComponent;
import feign.utils.RecordEvaluator;
import feign.utils.RecordInvokeUtils;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.Encoded;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.MatrixParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;

public final class JakartaContract extends AlwaysEncodeBodyContract {

  static final String ACCEPT = "Accept";
  static final String CONTENT_TYPE = "Content-Type";
  private int index = 0;

  // Protected so unittest can call us
  // XXX: Should parseAndValidateMetadata(Class, Method) be public instead? The old deprecated
  // parseAndValidateMetadata(Method) was public..
  @Override
  protected MethodMetadata parseAndValidateMetadata(Class<?> targetType, Method method) {
    return super.parseAndValidateMetadata(targetType, method);
  }

  public JakartaContract() {
    super.registerParameterAnnotation(Suspended.class, (ann, data, i) -> data.ignoreParamater(i));
    super.registerParameterAnnotation(Context.class, (ann, data, i) -> data.ignoreParamater(i));
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
    super.registerClassAnnotation(Encoded.class, this::handleEncodedAnnotation);

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
    super.registerMethodAnnotation(Encoded.class, this::handleEncodedAnnotation);
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

  private void handleEncodedAnnotation(Encoded encoded, MethodMetadata data) {
    data.template().setDisableDecodingForAll(true);
  }

  private void indexToExpander(MethodMetadata data, int paramIndex, Expander expander) {
    Map<Integer, Expander> indexToExpander = new HashMap<>();
    indexToExpander.put(paramIndex, expander);
    Optional.ofNullable(data.indexToExpander()).ifPresentOrElse(v -> v.putAll(indexToExpander),
        () -> data.indexToExpander(indexToExpander));
  }

  private static String getAnnotation(Object obj, Class<? extends Annotation> annotationClazz) {
    Annotation annotation = null;
    try {
      Method method = annotationClazz.getDeclaredMethod("value");
      if (obj instanceof Field) {
        annotation = ((Field) obj).getAnnotation(annotationClazz);
      } else {
        Annotation[] annotations = ((RecComponent) obj).annotations();
        annotation =
            Arrays.stream(annotations)
                .filter(v -> v.annotationType().equals(annotationClazz))
                .findFirst().get();
      }
      return (String) method.invoke(annotation);
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
        | NoSuchMethodException | SecurityException e) {
      e.printStackTrace();
      return null;
    }
  }

  private static boolean isAnnotationPresent(Object obj, Class<? extends Annotation> annotation) {
    if (obj instanceof Field) {
      return ((Field) obj).isAnnotationPresent(annotation);
    } else {
      Annotation[] annotations = ((RecComponent) obj).annotations();
      return Arrays.stream(annotations).anyMatch(v -> v.annotationType().equals(annotation));
    }
  }

  private static String getName(Object obj) {
    if (obj instanceof Field) {
      return ((Field) obj).getName();
    } else {
      return ((RecComponent) obj).name();
    }
  }

  private static boolean isSupportRecordComponent() {
    int version = JdkVersionResolver.resolve();
    return version > 15;
  }

  protected void registerParamAnnotations() {

    registerParameterAnnotation(PathParam.class, (param, data, paramIndex) -> {
      final String name = param.value();
      checkState(emptyToNull(name) != null, "PathParam.value() was empty on parameter %s",
          paramIndex);
      nameParam(data, name, paramIndex);
      indexToExpander(data, paramIndex, new DefaultParameterExpander());
    });
    registerParameterAnnotation(QueryParam.class, (param, data, paramIndex) -> {
      final String name = param.value();
      checkState(emptyToNull(name) != null, "QueryParam.value() was empty on parameter %s",
          paramIndex);
      final String query = addTemplatedParam(name);
      data.template().query(name, query);
      nameParam(data, name, paramIndex);
      indexToExpander(data, paramIndex, new DefaultParameterExpander());
    });
    registerParameterAnnotation(HeaderParam.class, (param, data, paramIndex) -> {
      final String name = param.value();
      checkState(emptyToNull(name) != null, "HeaderParam.value() was empty on parameter %s",
          paramIndex);
      final String header = addTemplatedParam(name);
      data.template().header(name, header);
      nameParam(data, name, paramIndex);
      indexToExpander(data, paramIndex, new DefaultParameterExpander());
    });
    registerParameterAnnotation(FormParam.class, (param, data, paramIndex) -> {
      final String name = param.value();
      checkState(emptyToNull(name) != null, "FormParam.value() was empty on parameter %s",
          paramIndex);
      data.formParams().add(name);
      nameParam(data, name, paramIndex);
      indexToExpander(data, paramIndex, new DefaultParameterExpander());
    });
    registerParameterAnnotation(DefaultValue.class, (param, data, paramIndex) -> {
      final String defaultValue = param.value();
      indexToExpander(data, paramIndex, new DefaultValueExpander(defaultValue,
          Optional.ofNullable(data.indexToExpander()).map(v -> v.get(paramIndex))));
    });
    registerParameterAnnotation(MatrixParam.class, (param, data, paramIndex) -> {
      final String name = param.value();
      checkState(emptyToNull(name) != null, "MatrixParam.value() was empty on parameter %s",
          paramIndex);
      nameParam(data, name, paramIndex);
      indexToExpander(data, paramIndex, new DefaultParameterExpander());
    });
    registerParameterAnnotation(CookieParam.class, (param, data, paramIndex) -> {
      final String name = param.value();
      checkState(emptyToNull(name) != null, "CookieParam.value() was empty on parameter %s",
          paramIndex);
      final String cookie = addTemplatedParam(name);
      data.template().header("Cookie", name + '=' + cookie);
      nameParam(data, name, paramIndex);
      indexToExpander(data, paramIndex, new CookieParamExpander(name));
    });
    registerParameterAnnotation(Encoded.class, (param, data, paramIndex) -> {
      data.indexToEncoded().put(paramIndex, true);
    });
    registerParameterAnnotation(BeanParam.class, (param, data, paramIndex) -> {

      if (index == 0) {
        index = paramIndex;
      }
      data.indexToExpand().add(index);
      Class<?> type = data.method()
          .getParameters()[paramIndex].getType();
      boolean isSupportRecord = RecordEvaluator.isSupport(),
          isRecord = isSupportRecord && RecordInvokeUtils.isRecord(type);
      if (isRecord && !isSupportRecordComponent()) {
        throw new UnsupportedClassVersionError();
      }

      Object[] aggregatedParams = isRecord ? RecordInvokeUtils.recordComponents(type, null)
          : Arrays.stream(type.getDeclaredFields())

              .filter(v -> !"this$0".equals(v.getName()))
              .toArray(Field[]::new);

      for (Object aggregatedParam : aggregatedParams) {


        if (isAnnotationPresent(aggregatedParam, PathParam.class)) {
          final String name = getAnnotation(aggregatedParam, PathParam.class);
          checkState(
              emptyToNull(name) != null,
              "BeanParam parameter %s contains PathParam with empty .value() on field %s",
              paramIndex, getName(aggregatedParam));
          nameParam(data, name, index);
          index++;
        }
        if (isAnnotationPresent(aggregatedParam, QueryParam.class)) {
          final String name = getAnnotation(aggregatedParam, QueryParam.class);
          checkState(
              emptyToNull(name) != null,
              "BeanParam parameter %s contains QueryParam with empty .value() on field %s",
              paramIndex,
              getName(aggregatedParam));
          final String query = addTemplatedParam(name);
          data.template().query(name, query);
          nameParam(data, name, index);
          index++;
        }
        if (isAnnotationPresent(aggregatedParam, HeaderParam.class)) {
          final String name = getAnnotation(aggregatedParam, HeaderParam.class);
          checkState(
              emptyToNull(name) != null,
              "BeanParam parameter %s contains HeaderParam with empty .value() on field %s",
              paramIndex,
              getName(aggregatedParam));
          final String header = addTemplatedParam(name);
          data.template().header(name, header);
          nameParam(data, name, index);
          index++;
        }
        if (isAnnotationPresent(aggregatedParam, FormParam.class)) {
          final String name = getAnnotation(aggregatedParam, FormParam.class);
          checkState(
              emptyToNull(name) != null,
              "BeanParam parameter %s contains FormParam with empty .value() on field %s",
              paramIndex,
              getName(aggregatedParam));
          data.formParams().add(name);
          nameParam(data, name, index);
          index++;
        }
        if (isAnnotationPresent(aggregatedParam, MatrixParam.class)) {
          final String name = getAnnotation(aggregatedParam, MatrixParam.class);
          checkState(
              emptyToNull(name) != null,
              "BeanParam parameter %s contains MatrixParam with empty .value() on field %s",
              paramIndex,
              getName(aggregatedParam));
          nameParam(data, name, index);
          index++;
        }
        if (isAnnotationPresent(aggregatedParam, CookieParam.class)) {
          final String name = getAnnotation(aggregatedParam, CookieParam.class);
          checkState(
              emptyToNull(name) != null,
              "BeanParam parameter %s contains CookieParam with empty .value() on field %s",
              paramIndex,
              getName(aggregatedParam));
          final String cookie = addTemplatedParam(name);
          data.template().header("Cookie", name + '=' + cookie);
          nameParam(data, name, index);
          indexToExpander(data, index, new CookieParamExpander(name));
          index++;
        }

        if (isAnnotationPresent(aggregatedParam, DefaultValue.class)) {

          final String defaultValue = getAnnotation(aggregatedParam, DefaultValue.class);

          int lastIndex = index - 1;
          indexToExpander(data, lastIndex, new DefaultValueExpander(defaultValue,
              Optional.ofNullable(
                  data.indexToExpander()).map(v -> v.get(lastIndex))));
        }
        if (isAnnotationPresent(aggregatedParam, Encoded.class)) {
          checkState(!isAnnotationPresent(aggregatedParam, CookieParam.class),
              "@Encoded unsupported for @CookieParam!");
          int lastIndex = index - 1;
          data.indexToEncoded().put(lastIndex, true);
        }
      }
    });
  }

  // Not using override as the super-type's method is deprecated and will be removed.
  private String addTemplatedParam(String name) {
    return String.format("{%s}", name);
  }
}
