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
package feign.jaxrs2;

import static feign.Util.checkState;
import static feign.Util.emptyToNull;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import javax.ws.rs.BeanParam;
import javax.ws.rs.CookieParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.Encoded;
import javax.ws.rs.FormParam;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.MatrixParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import feign.jaxrs.AbstractParameterValidator.DefaultParameterExpander;
import feign.jaxrs.CookieParamExpander;
import feign.jaxrs.DefaultValueExpander;
import feign.jaxrs.JAXRSContract;
import feign.utils.JdkVersionResolver;
import feign.utils.RecComponent;
import feign.utils.RecordEvaluator;
import feign.utils.RecordInvokeUtils;

/**
 * Please refer to the <a href="https://github.com/Netflix/feign/tree/master/feign-jaxrs2">Feign
 * JAX-RS 2 README</a>.
 */
public final class JAXRS2Contract extends JAXRSContract {
  private int index = 0;

  public JAXRS2Contract() {

    // parameter with unsupported jax-rs annotations should not be passed as body params.
    // this will prevent interfaces from becoming unusable entirely due to single (unsupported)
    // endpoints.
    // https://github.com/OpenFeign/feign/issues/669
    super.registerParameterAnnotation(Suspended.class, (ann, data, i) -> data.ignoreParamater(i));
    super.registerParameterAnnotation(Context.class, (ann, data, i) -> data.ignoreParamater(i));

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

  @Override
  protected void registerParamAnnotations() {
    super.registerParamAnnotations();
    registerParameterAnnotation(BeanParam.class, (param, data, paramIndex) -> {
      if (index == 0) {
        index = paramIndex;
      }
      data.indexToExpand().add(index);
      data.alwaysEncodeBody();
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
          indexToExpander(data, index, new DefaultParameterExpander());
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
          indexToExpander(data, index, new DefaultParameterExpander());
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
          indexToExpander(data, index, new DefaultParameterExpander());
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
          indexToExpander(data, index, new DefaultParameterExpander());
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
          indexToExpander(data, index, new DefaultParameterExpander());
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
          int lastIndex = index - 1;
          data.indexToEncoded().put(lastIndex, true);
        }
      }
    });
  }
}
