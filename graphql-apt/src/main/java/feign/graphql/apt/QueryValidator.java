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

import feign.Param;
import graphql.GraphQLError;
import graphql.analysis.QueryTraversalOptions;
import graphql.analysis.QueryTraverser;
import graphql.analysis.QueryVisitorFieldArgumentEnvironment;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.analysis.QueryVisitorStub;
import graphql.execution.CoercedVariables;
import graphql.language.ArrayValue;
import graphql.language.Document;
import graphql.language.EnumValue;
import graphql.language.ListType;
import graphql.language.Node;
import graphql.language.NonNullType;
import graphql.language.ObjectValue;
import graphql.language.OperationDefinition;
import graphql.language.SourceLocation;
import graphql.language.Type;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeUtil;
import graphql.util.TraversalControl;
import graphql.validation.Validator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.tools.Diagnostic;

public class QueryValidator {

  private final Messager messager;

  public QueryValidator(Messager messager) {
    this.messager = messager;
  }

  public boolean validate(
      GraphQLSchema schema, Document document, Element methodElement, boolean generateDeprecated) {
    var errors = new Validator().validateDocument(schema, document, Locale.ENGLISH);
    for (GraphQLError error : errors) {
      var locations = error.getLocations();
      reportError(
          error.getMessage(),
          locations == null || locations.isEmpty() ? null : locations.get(0),
          methodElement);
    }
    if (!errors.isEmpty() || generateDeprecated) {
      return errors.isEmpty();
    }

    var deprecatedUsages = findDeprecatedUsages(schema, document);
    for (var usage : deprecatedUsages) {
      reportError(
          usage.message() + " and generateDeprecated is false", usage.location(), methodElement);
    }
    return deprecatedUsages.isEmpty();
  }

  private void reportError(String message, SourceLocation location, Element methodElement) {
    if (location == null) {
      messager.printMessage(
          Diagnostic.Kind.ERROR, "GraphQL validation error: " + message, methodElement);
      return;
    }
    messager.printMessage(
        Diagnostic.Kind.ERROR,
        "GraphQL validation error at line %d, column %d: %s"
            .formatted(location.getLine(), location.getColumn(), message),
        methodElement);
  }

  private List<DeprecatedUsage> findDeprecatedUsages(GraphQLSchema schema, Document document) {
    var usages = new ArrayList<DeprecatedUsage>();
    QueryTraverser.newQueryTraverser()
        .schema(schema)
        .document(document)
        .coercedVariables(CoercedVariables.emptyVariables())
        .options(QueryTraversalOptions.defaultOptions().coerceFieldArguments(false))
        .build()
        .visitPreOrder(
            new QueryVisitorStub() {
              @Override
              public void visitField(QueryVisitorFieldEnvironment env) {
                var definition = env.getFieldDefinition();
                if (!env.isTypeNameIntrospectionField() && definition.isDeprecated()) {
                  usages.add(
                      DeprecatedUsage.of(
                          "Field",
                          definition.getName(),
                          definition.getDeprecationReason(),
                          env.getField()));
                }
              }

              @Override
              public TraversalControl visitArgument(QueryVisitorFieldArgumentEnvironment env) {
                var argument = env.getGraphQLArgument();
                if (argument.isDeprecated()) {
                  usages.add(
                      DeprecatedUsage.of(
                          "Argument",
                          argument.getName(),
                          argument.getDeprecationReason(),
                          env.getArgument()));
                }
                collectDeprecatedValues(env.getArgument().getValue(), argument.getType(), usages);
                return TraversalControl.CONTINUE;
              }
            });

    for (var definition : document.getDefinitionsOfType(OperationDefinition.class)) {
      for (var variable : definition.getVariableDefinitions()) {
        if (variable.getDefaultValue() != null
            && schema.getType(GraphqlTypeMapper.unwrapTypeName(variable.getType()))
                instanceof GraphQLInputType variableType) {
          collectDeprecatedValues(variable.getDefaultValue(), variableType, usages);
        }
      }
    }
    return usages;
  }

  private static void collectDeprecatedValues(
      Value<?> value, GraphQLInputType type, List<DeprecatedUsage> usages) {
    var unwrapped = GraphQLTypeUtil.unwrapAll(type);
    if (value instanceof ArrayValue array) {
      for (var element : array.getValues()) {
        collectDeprecatedValues(element, (GraphQLInputType) unwrapped, usages);
      }
    } else if (value instanceof EnumValue enumValue
        && unwrapped instanceof GraphQLEnumType enumType) {
      var definition = enumType.getValue(enumValue.getName());
      if (definition != null && definition.isDeprecated()) {
        usages.add(
            DeprecatedUsage.of(
                "Enum value", definition.getName(), definition.getDeprecationReason(), enumValue));
      }
    } else if (value instanceof ObjectValue object
        && unwrapped instanceof GraphQLInputObjectType inputType) {
      for (var field : object.getObjectFields()) {
        var definition = inputType.getField(field.getName());
        if (definition == null) {
          continue;
        }
        if (definition.isDeprecated()) {
          usages.add(
              DeprecatedUsage.of(
                  "Input field", definition.getName(), definition.getDeprecationReason(), field));
        }
        collectDeprecatedValues(field.getValue(), definition.getType(), usages);
      }
    }
  }

  private record DeprecatedUsage(String message, SourceLocation location) {

    static DeprecatedUsage of(String kind, String name, String reason, Node<?> node) {
      return new DeprecatedUsage(
          "%s '%s' is deprecated (%s)".formatted(kind, name, reason), node.getSourceLocation());
    }
  }

  public void validateVariableBindings(OperationDefinition operation, ExecutableElement method) {
    var paramNames = new HashSet<String>();
    for (var param : method.getParameters()) {
      if (param.getAnnotation(Param.class) == null) {
        paramNames.add(param.getSimpleName().toString());
      }
    }

    for (VariableDefinition varDef : operation.getVariableDefinitions()) {
      if (varDef.getType() instanceof NonNullType && varDef.getDefaultValue() == null) {
        var varName = varDef.getName();
        if (!paramNames.contains(varName)) {
          messager.printMessage(
              Diagnostic.Kind.ERROR,
              "Required GraphQL variable '$%s' (%s) has no corresponding method parameter. Add a parameter named '%s' or provide a default value in the query."
                  .formatted(varName, typeToString(varDef.getType()), varName),
              method);
        }
      }
    }
  }

  private String typeToString(Type<?> type) {
    if (type instanceof NonNullType nonNullType) {
      return typeToString(nonNullType.getType()) + "!";
    }
    if (type instanceof ListType listType) {
      return "[" + typeToString(listType.getType()) + "]";
    }
    if (type instanceof graphql.language.TypeName typeName) {
      return typeName.getName();
    }
    return type.toString();
  }
}
