/**
 * Copyright 2012-2020 The Feign Authors
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

import javax.ws.rs.BeanParam;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import feign.jaxrs.JAXRSContract;

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
    super.registerParameterAnnotation(BeanParam.class, (ann, data, i) -> data.ignoreParamater(i));
  }

}
