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

import feign.jaxrs.JAXRSContract;
import javax.ws.rs.*;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import java.lang.reflect.Field;
import static feign.Util.checkState;
import static feign.Util.emptyToNull;

/**
 * Please refer to the <a href="https://github.com/Netflix/feign/tree/master/feign-jaxrs2">Feign
 * JAX-RS 2 README</a>.
 */
public final class JAXRS2Contract extends JAXRSContract {

  public JAXRS2Contract() {
    // parameter with unsupported jax-rs annotations should not be passed as body params.
    // this will prevent interfaces from becoming unusable entirely due to single (unsupported)
    // endpoints.
    // https://github.com/OpenFeign/feign/issues/669
    super.registerParameterAnnotation(Suspended.class, (ann, data, i) -> data.ignoreParamater(i));
    super.registerParameterAnnotation(Context.class, (ann, data, i) -> data.ignoreParamater(i));

  }

  @Override
  protected void registerParamAnnotations() {
    super.registerParamAnnotations();

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
}
