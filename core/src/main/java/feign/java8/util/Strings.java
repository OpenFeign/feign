/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved. DO NOT ALTER OR REMOVE
 * COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License version 2 only, as published by the Free Software Foundation. Oracle
 * designates this particular file as subject to the "Classpath" exception as provided by Oracle in
 * the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version 2 along with this work;
 * if not, write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA or visit www.oracle.com
 * if you need additional information or have any questions.
 */

package feign.java8.util;

import java.util.Objects;

/**
 * Partial repackage of static java 8 string methods (for android compat) Created by masc on
 * 19.10.18.
 */
public final class Strings {
  /**
   * Returns a new String composed of copies of the {@code CharSequence elements} joined together
   * with a copy of the specified {@code delimiter}.
   *
   * <blockquote>For example,
   * 
   * <pre>
   * {
   *   &#64;code
   *   String message = String.join("-", "Java", "is", "cool");
   *   // message returned is: "Java-is-cool"
   * }
   * </pre>
   * 
   * </blockquote>
   *
   * Note that if an element is null, then {@code "null"} is added.
   *
   * @param delimiter the delimiter that separates each element
   * @param elements the elements to join together.
   *
   * @return a new {@code String} that is composed of the {@code elements} separated by the
   *         {@code delimiter}
   *
   * @throws NullPointerException If {@code delimiter} or {@code elements} is {@code null}
   *
   * @see java.util.StringJoiner
   * @since 1.8
   */
  public static java.lang.String join(CharSequence delimiter, CharSequence... elements) {
    Objects.requireNonNull(delimiter);
    Objects.requireNonNull(elements);
    // Number of elements not likely worth Arrays.stream overhead.
    StringJoiner joiner = new StringJoiner(delimiter);
    for (CharSequence cs : elements) {
      joiner.add(cs);
    }
    return joiner.toString();
  }

  /**
   * Returns a new {@code String} composed of copies of the {@code CharSequence elements} joined
   * together with a copy of the specified {@code delimiter}.
   *
   * <blockquote>For example,
   * 
   * <pre>
   * {
   *   &#64;code
   *   List<String> strings = new LinkedList<>();
   *   strings.add("Java");
   *   strings.add("is");
   *   strings.add("cool");
   *   String message = String.join(" ", strings);
   *   // message returned is: "Java is cool"
   *
   *   Set<String> strings = new LinkedHashSet<>();
   *   strings.add("Java");
   *   strings.add("is");
   *   strings.add("very");
   *   strings.add("cool");
   *   String message = String.join("-", strings);
   *   // message returned is: "Java-is-very-cool"
   * }
   * </pre>
   * 
   * </blockquote>
   *
   * Note that if an individual element is {@code null}, then {@code "null"} is added.
   *
   * @param delimiter a sequence of characters that is used to separate each of the {@code elements}
   *        in the resulting {@code String}
   * @param elements an {@code Iterable} that will have its {@code elements} joined together.
   *
   * @return a new {@code String} that is composed from the {@code elements} argument
   *
   * @throws NullPointerException If {@code delimiter} or {@code elements} is {@code null}
   *
   * @see #join(CharSequence,CharSequence...)
   * @see java.util.StringJoiner
   * @since 1.8
   */
  public static java.lang.String join(CharSequence delimiter,
                                      Iterable<? extends CharSequence> elements) {
    Objects.requireNonNull(delimiter);
    Objects.requireNonNull(elements);
    java.util.StringJoiner joiner = new java.util.StringJoiner(delimiter);
    for (CharSequence cs : elements) {
      joiner.add(cs);
    }
    return joiner.toString();
  }
}
