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
package feign;

import java.lang.annotation.Retention;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Expands the uri template supplied in the {@code value}, permitting path and query variables, or
 * just the http method. Templates should conform to
 * <a href="https://tools.ietf.org/html/rfc6570">RFC 6570</a>. Support is limited to Simple String
 * expansion and Reserved Expansion (Level 1 and Level 2) expressions.
 */
@java.lang.annotation.Target(METHOD)
@Retention(RUNTIME)
public @interface RequestLine {

  String value();

  boolean decodeSlash() default true;

  CollectionFormat collectionFormat() default CollectionFormat.EXPLODED;
}
