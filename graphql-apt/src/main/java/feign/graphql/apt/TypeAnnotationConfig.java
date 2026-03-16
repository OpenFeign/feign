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
package feign.graphql.apt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

record TypeAnnotationConfig(Set<String> imports, List<String> annotations, boolean useOptional) {

  static final TypeAnnotationConfig EMPTY = new TypeAnnotationConfig(Set.of(), List.of(), false);

  static TypeAnnotationConfig resolve(
      List<String> typeAnnotationFqns, String[] rawTypeAnnotations, boolean useOptional) {

    var rawSimpleNames = new HashSet<String>();
    for (var raw : rawTypeAnnotations) {
      var stripped = raw.startsWith("@") ? raw.substring(1) : raw;
      var parenIdx = stripped.indexOf('(');
      rawSimpleNames.add(parenIdx > 0 ? stripped.substring(0, parenIdx).trim() : stripped.trim());
    }

    var imports = new TreeSet<String>();
    var annotations = new ArrayList<String>();

    for (var fqn : typeAnnotationFqns) {
      var simpleName = fqn.substring(fqn.lastIndexOf('.') + 1);
      if (!fqn.startsWith("java.lang.")) {
        imports.add(fqn);
      }
      if (!rawSimpleNames.contains(simpleName)) {
        annotations.add("@" + simpleName);
      }
    }

    for (var raw : rawTypeAnnotations) {
      annotations.add(raw.startsWith("@") ? raw : "@" + raw);
    }

    return new TypeAnnotationConfig(imports, annotations, useOptional);
  }
}
