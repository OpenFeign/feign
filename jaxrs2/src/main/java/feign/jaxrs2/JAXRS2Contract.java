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
package feign.jaxrs2;

import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import feign.jaxrs.JAXRSContract;
import java.lang.annotation.Annotation;

/**
 * Please refer to the <a href="https://github.com/Netflix/feign/tree/master/feign-jaxrs2">Feign
 * JAX-RS 2 README</a>.
 */
public final class JAXRS2Contract extends JAXRSContract {
  @Override
  protected boolean isUnsupportedHttpParameterAnnotation(Annotation parameterAnnotation) {
    Class<? extends Annotation> annotationType = parameterAnnotation.annotationType();

    // masc20180327. parameter with unsupported jax-rs annotations should not be passed as body
    // params.
    // this will prevent interfaces from becoming unusable entirely due to single (unsupported)
    // endpoints.
    // https://github.com/OpenFeign/feign/issues/669
    return (annotationType == Suspended.class ||
        annotationType == Context.class);
  }
}
