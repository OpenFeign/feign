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
import java.util.List;

public class GraphqlTypeMapper {

  private final String targetPackage;

  public GraphqlTypeMapper(String targetPackage) {
    this.targetPackage = targetPackage;
  }

  public TypeName map(Type<?> type) {
    if (type instanceof NonNullType) {
      return map(((NonNullType) type).getType());
    }
    if (type instanceof ListType) {
      TypeName elementType = map(((ListType) type).getType());
      return ParameterizedTypeName.get(ClassName.get(List.class), elementType);
    }
    if (type instanceof graphql.language.TypeName) {
      return mapScalarOrNamed(((graphql.language.TypeName) type).getName());
    }
    return ClassName.get(String.class);
  }

  private TypeName mapScalarOrNamed(String name) {
    switch (name) {
      case "String":
        return ClassName.get(String.class);
      case "Int":
        return ClassName.get(Integer.class);
      case "Float":
        return ClassName.get(Double.class);
      case "Boolean":
        return ClassName.get(Boolean.class);
      case "ID":
        return ClassName.get(String.class);
      default:
        return ClassName.get(targetPackage, name);
    }
  }

  public boolean isScalar(String name) {
    switch (name) {
      case "String":
      case "Int":
      case "Float":
      case "Boolean":
      case "ID":
        return true;
      default:
        return false;
    }
  }
}
