/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Declares a method parameter as one part of a {@code multipart/form-data} request body.
 *
 * <p>Part headers may contain Feign template expressions, such as {@code {name}}, which are
 * resolved from other named parameters.
 *
 * <pre>
 * &#64;RequestLine("POST /")
 * void upload(
 *     &#64;Part({
 *       "Content-Disposition: form-data; name=\"{name}\"; filename=\"{filename}\"",
 *       "Content-Type: text/plain"
 *     })
 *     Path file,
 *     &#64;Param("name") String name,
 *     &#64;Param("filename") String filename);
 * </pre>
 *
 * @apiNote Feign generates the multipart boundary; users should not include a boundary in part
 *     headers.
 */
@Target(PARAMETER)
@Retention(RUNTIME)
public @interface Part {
  /**
   * Part header templates. This is a concise alias for {@link #headers()}.
   *
   * @apiNote if both {@code value} and {@code headers} are set, the contract should reject the
   *     method.
   */
  String[] value() default {};

  /**
   * Part header templates.
   *
   * @apiNote each entry should be one header line, for example {@code Content-Type: text/plain}.
   */
  String[] headers() default {};

  /**
   * Whether arrays and iterable values should be expanded into repeated parts.
   *
   * @apiNote {@code true} by default. When {@code false}, container values are encoded as a single
   *     part.
   */
  boolean explode() default true;
}
