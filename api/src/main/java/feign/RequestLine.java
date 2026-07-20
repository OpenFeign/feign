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

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;

/**
 * Expands the uri template supplied in the {@code value}, permitting path and query variables, or
 * just the http method. Templates should conform to <a
 * href="https://tools.ietf.org/html/rfc6570">RFC 6570</a>. Support is limited to Simple String
 * expansion and Reserved Expansion (Level 1 and Level 2) expressions.
 */
@java.lang.annotation.Target(METHOD)
@Retention(RUNTIME)
public @interface RequestLine {

  /**
   * The HTTP request line, including the method and an optional URI template.
   *
   * <p>The string must begin with a valid {@linkplain feign.Request.HttpMethod HTTP method name}
   * (e.g. {@linkplain feign.Request.HttpMethod#GET GET}, {@linkplain feign.Request.HttpMethod#POST
   * POST}, {@linkplain feign.Request.HttpMethod#PUT PUT}), followed by a space and a URI template.
   * If only the HTTP method is specified (e.g. {@code "DELETE"}), the request will use the base URL
   * defined for the client.
   *
   * <p>Example:
   *
   * <pre>{@code @RequestLine("GET /repos/{owner}/{repo}")
   * Repo getRepo(@Param("owner") String owner, @Param("repo") String repo);
   * }</pre>
   *
   * @return the HTTP method and optional URI template for the request.
   * @see feign.template.UriTemplate
   */
  String value();

  /**
   * Controls whether percent-encoded forward slashes ({@code %2F}) in expanded path variables are
   * decoded back to {@code '/'} before sending the request.
   *
   * <p>When {@code true} (the default), any {@code %2F} sequences produced during URI template
   * expansion will be replaced with literal slashes, meaning that path variables containing slashes
   * will be interpreted as multiple path segments.
   *
   * <p>When {@code false}, percent-encoded slashes ({@code %2F}) are preserved in the final URL.
   * This is useful when a path variable intentionally includes a slash as part of its value (for
   * example, an encoded identifier such as {@code "foo%2Fbar"}).
   *
   * <p>Example:
   *
   * <pre>{@code @RequestLine(value = "GET /projects/{id}", decodeSlash = false)
   * Project getProject(@Param("id") String encodedId);
   * }</pre>
   *
   * @return {@code true} if encoded slashes should be decoded (default behavior); {@code false} to
   *     preserve {@code %2F} sequences in the URL.
   */
  boolean decodeSlash() default true;

  /**
   * Specifies how collections (e.g. {@link java.util.List List} or arrays) are serialized when
   * expanded into the URI template.
   *
   * <p>Determines whether values are represented as exploded parameters (repeated keys) or as a
   * single comma-separated value, depending on the chosen {@link feign.CollectionFormat}.
   *
   * <p>Example:
   *
   * <ul>
   *   <li>{@linkplain CollectionFormat#EXPLODED EXPLODED}: {@code /items?id=1&id=2&id=3}
   *   <li>{@linkplain CollectionFormat#CSV CSV}: {@code /items?id=1,2,3}
   * </ul>
   *
   * @return the collection serialization format to use when expanding templates.
   * @see CollectionFormat
   */
  CollectionFormat collectionFormat() default CollectionFormat.EXPLODED;
}
