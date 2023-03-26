/*
 * Copyright (c) 2010, 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package javax.ws.rs;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds the value(s) of a URI matrix parameter to a resource method parameter,
 * resource class field, or resource class bean property.
 * Values are URL decoded unless this is disabled using the {@link Encoded}
 * annotation. A default value can be specified using the {@link DefaultValue}
 * annotation.
 * <p>
 * Note that the {@code @MatrixParam} {@link #value() annotation value} refers
 * to a name of a matrix parameter that resides in the last matched path segment
 * of the {@link Path}-annotated Java structure that injects the value of the matrix
 * parameter.
 * </p>
 * <p>
 * The type {@code T} of the annotated parameter, field or property must
 * either:
 * </p>
 * <ol>
 * <li>Be a primitive type</li>
 * <li>Have a constructor that accepts a single {@code String} argument</li>
 * <li>Have a static method named {@code valueOf} or {@code fromString}
 * that accepts a single
 * {@code String} argument (see, for example, {@link Integer#valueOf(String)})</li>
 * <li>Have a registered implementation of {@link javax.ws.rs.ext.ParamConverterProvider}
 * JAX-RS extension SPI that returns a {@link javax.ws.rs.ext.ParamConverter}
 * instance capable of a "from string" conversion for the type.</li>
 * <li>Be {@code List<T>}, {@code Set<T>} or
 * {@code SortedSet<T>}, where {@code T} satisfies 2, 3 or 4 above.
 * The resulting collection is read-only.</li>
 * </ol>
 *
 * <p>If the type is not one of the collection types listed in 5 above and the
 * matrix parameter is represented by multiple values then the first value (lexically)
 * of the parameter is used.</p>
 *
 * <p>Because injection occurs at object creation time, use of this annotation
 * on resource class fields and bean properties is only supported for the
 * default per-request resource class lifecycle. Resource classes using
 * other lifecycles should only use this annotation on resource method
 * parameters.</p>
 *
 * @author Paul Sandoz
 * @author Marc Hadley
 * @see DefaultValue
 * @see Encoded
 * @see <a href="http://www.w3.org/DesignIssues/MatrixURIs.html">Matrix URIs</a>
 * @since 1.0
 */
@Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD,
  ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MatrixParam {

    /**
     * Defines the name of the URI matrix parameter whose value will be used
     * to initialize the value of the annotated method argument, class field or
     * bean property. The name is specified in decoded form, any percent encoded
     * literals within the value will not be decoded and will instead be
     * treated as literal text. E.g. if the parameter name is "a b" then the
     * value of the annotation is "a b", <i>not</i> "a+b" or "a%20b".
     */
    String value();
}
