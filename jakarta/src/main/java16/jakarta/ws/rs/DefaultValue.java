/*
 * Copyright (c) 2010, 2019 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v. 2.0, which is available at http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary Licenses when the
 * conditions for such availability set forth in the Eclipse Public License v. 2.0 are satisfied:
 * GNU General Public License, version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package jakarta.ws.rs;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines the default value of request meta-data that is bound using one of the following
 * annotations: {@link jakarta.ws.rs.PathParam}, {@link jakarta.ws.rs.QueryParam},
 * {@link jakarta.ws.rs.MatrixParam}, {@link jakarta.ws.rs.CookieParam},
 * {@link jakarta.ws.rs.FormParam}, or {@link jakarta.ws.rs.HeaderParam}. The default value is used
 * if the corresponding meta-data is not present in the request.
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
   *
   * @return default value.
   */
  String value();
}
