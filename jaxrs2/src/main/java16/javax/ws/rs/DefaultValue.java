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
package javax.ws.rs;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines the default value of request meta-data that is bound using one of the following
 * annotations: {@link javax.ws.rs.PathParam}, {@link javax.ws.rs.QueryParam},
 * {@link javax.ws.rs.MatrixParam}, {@link javax.ws.rs.CookieParam}, {@link javax.ws.rs.FormParam},
 * or {@link javax.ws.rs.HeaderParam}. The default value is used if the corresponding meta-data is
 * not present in the request.
 * <p>
 * If the type of the annotated parameter is {@link java.util.List}, {@link java.util.Set} or
 * {@link java.util.SortedSet} then the resulting collection will have a single entry mapped from
 * the supplied default value.
 * </p>
 * <p>
 * If this annotation is not used and the corresponding meta-data is not present in the request, the
 * value will be an empty collection for {@code List}, {@code Set} or {@code SortedSet},
 * {@code null} for other object types, and the Java-defined default for primitive types.
 * </p>
 *
 * @author Paul Sandoz
 * @author Marc Hadley
 * @see PathParam
 * @see QueryParam
 * @see FormParam
 * @see HeaderParam
 * @see MatrixParam
 * @see CookieParam
 * @since 1.0
 */
@Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD,
    ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DefaultValue {

  /**
   * The specified default value.
   */
  String value();
}
