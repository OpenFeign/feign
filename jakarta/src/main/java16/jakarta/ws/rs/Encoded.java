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
 * Disables automatic decoding of parameter values bound using {@link QueryParam},
 * {@link PathParam}, {@link FormParam} or {@link MatrixParam}. Using this annotation on a method
 * will disable decoding for all parameters. Using this annotation on a class will disable decoding
 * for all parameters of all methods.
 *
 * @author Paul Sandoz
 * @author Marc Hadley
 * @see QueryParam
 * @see MatrixParam
 * @see PathParam
 * @see FormParam
 * @since 1.0
 */
@Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR,
    ElementType.TYPE, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Encoded {
}
