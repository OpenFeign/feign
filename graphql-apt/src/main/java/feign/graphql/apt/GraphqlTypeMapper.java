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

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GraphqlTypeMapper {

  private static final Map<String, TypeName> BUILT_IN_SCALARS =
      Map.of(
          "String", ClassName.get(String.class),
          "Int", ClassName.get(Integer.class),
          "Float", ClassName.get(Double.class),
          "Boolean", ClassName.get(Boolean.class),
          "ID", ClassName.get(String.class));

  private final String targetPackage;
  private final Map<String, TypeName> customScalars;

  public GraphqlTypeMapper(String targetPackage, Map<String, TypeName> customScalars) {
    this.targetPackage = targetPackage;
    this.customScalars = new HashMap<>(customScalars);
  }

  public TypeName map(Type<?> type) {
    if (type instanceof NonNullType nullType) {
      return map(nullType.getType());
    }
    if (type instanceof ListType listType) {
      var elementType = map(listType.getType());
      return ParameterizedTypeName.get(ClassName.get(List.class), elementType);
    }
    if (type instanceof graphql.language.TypeName name) {
      return mapScalarOrNamed(name.getName());
    }
    return ClassName.get(String.class);
  }

  private TypeName mapScalarOrNamed(String name) {
    var builtIn = BUILT_IN_SCALARS.get(name);
    if (builtIn != null) {
      return builtIn;
    }
    var custom = customScalars.get(name);
    if (custom != null) {
      return custom;
    }
    return ClassName.get(targetPackage, name);
  }

  public boolean isScalar(String name) {
    return BUILT_IN_SCALARS.containsKey(name) || customScalars.containsKey(name);
  }
}
