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
/*
 * The contents of this file are subject to the terms of the Common Development and Distribution
 * License (the "License"). You may not use this file except in compliance with the License.
 * 
 * You can obtain a copy of the license at http://www.opensource.org/licenses/cddl1.php See the
 * License for the specific language governing permissions and limitations under the License.
 */

/*
 * Encoded.java
 *
 * Created on June 29, 2007, 11:40 AM
 *
 */

package javax.ws.rs;

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
 * @see QueryParam
 * @see MatrixParam
 * @see PathParam
 * @see FormParam
 */
@Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR,
    ElementType.TYPE,
    ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Encoded {

}
