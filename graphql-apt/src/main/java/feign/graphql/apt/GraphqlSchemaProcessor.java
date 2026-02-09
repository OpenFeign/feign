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

import com.google.auto.service.AutoService;
import com.squareup.javapoet.TypeName;
import feign.graphql.GraphqlQuery;
import feign.graphql.GraphqlSchema;
import feign.graphql.Scalar;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FieldDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationDefinition;
import graphql.language.SelectionSet;
import graphql.language.Type;
import graphql.language.VariableDefinition;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.UnExecutableSchemaGenerator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

@AutoService(Processor.class)
@SupportedAnnotationTypes("feign.graphql.GraphqlSchema")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class GraphqlSchemaProcessor extends AbstractProcessor {

  private Filer filer;
  private Messager messager;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    this.filer = processingEnv.getFiler();
    this.messager = processingEnv.getMessager();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (var element : roundEnv.getElementsAnnotatedWith(GraphqlSchema.class)) {
      if (!(element instanceof TypeElement)) {
        continue;
      }
      processInterface((TypeElement) element);
    }
    return true;
  }

  private void processInterface(TypeElement typeElement) {
    var schemaAnnotation = typeElement.getAnnotation(GraphqlSchema.class);
    var schemaPath = schemaAnnotation.value();

    var loader = new SchemaLoader(filer, messager);
    var schemaContent = loader.load(schemaPath, typeElement);
    if (schemaContent == null) {
      return;
    }

    var schemaParser = new SchemaParser();
    TypeDefinitionRegistry registry;
    try {
      registry = schemaParser.parse(schemaContent);
    } catch (Exception e) {
      messager.printMessage(
          Diagnostic.Kind.ERROR, "Failed to parse GraphQL schema: " + e.getMessage(), typeElement);
      return;
    }

    var customScalars = collectScalarMappings(typeElement);

    if (!validateCustomScalars(registry, customScalars, typeElement)) {
      return;
    }

    var graphqlSchema = UnExecutableSchemaGenerator.makeUnExecutableSchema(registry);

    var generateTypes = schemaAnnotation.generateTypes();

    var targetPackage = getPackageName(typeElement);
    var typeMapper = new GraphqlTypeMapper(targetPackage, customScalars);
    var validator = new QueryValidator(messager);
    var generator = new TypeGenerator(filer, messager, registry, typeMapper, targetPackage);

    for (var enclosed : typeElement.getEnclosedElements()) {
      if (!(enclosed instanceof ExecutableElement method)) {
        continue;
      }
      var queryAnnotation = method.getAnnotation(GraphqlQuery.class);
      if (queryAnnotation == null) {
        continue;
      }

      processMethod(
          method,
          queryAnnotation,
          graphqlSchema,
          registry,
          generator,
          validator,
          generateTypes,
          targetPackage);
    }
  }

  private Map<String, TypeName> collectScalarMappings(TypeElement typeElement) {
    var scalars = new HashMap<String, TypeName>();
    collectScalarsFromType(typeElement, scalars);
    collectScalarsFromParents(typeElement, scalars);
    return scalars;
  }

  private void collectScalarsFromType(TypeElement typeElement, Map<String, TypeName> scalars) {
    for (var enclosed : typeElement.getEnclosedElements()) {
      if (!(enclosed instanceof ExecutableElement method)) {
        continue;
      }
      var scalarAnnotation = method.getAnnotation(Scalar.class);
      if (scalarAnnotation == null) {
        continue;
      }
      var scalarName = scalarAnnotation.value();
      var javaType = TypeName.get(method.getReturnType());
      scalars.put(scalarName, javaType);
    }
  }

  private void collectScalarsFromParents(TypeElement typeElement, Map<String, TypeName> scalars) {
    for (var iface : typeElement.getInterfaces()) {
      if (iface instanceof DeclaredType type) {
        var element = type.asElement();
        if (element instanceof TypeElement parentType) {
          collectScalarsFromType(parentType, scalars);
          collectScalarsFromParents(parentType, scalars);
        }
      }
    }
  }

  private static final Set<String> GRAPHQL_BUILT_IN_SCALARS =
      Set.of("String", "Int", "Float", "Boolean", "ID");

  private boolean validateCustomScalars(
      TypeDefinitionRegistry registry,
      Map<String, TypeName> customScalars,
      TypeElement typeElement) {
    var schemaScalars = registry.scalars();
    var valid = true;
    for (var scalarName : schemaScalars.keySet()) {
      if (GRAPHQL_BUILT_IN_SCALARS.contains(scalarName)) {
        continue;
      }
      if (!customScalars.containsKey(scalarName)) {
        messager.printMessage(
            Diagnostic.Kind.ERROR,
            "Custom scalar '"
                + scalarName
                + "' is used in the schema but no @Scalar(\""
                + scalarName
                + "\") method is defined. "
                + "Add a @Scalar(\""
                + scalarName
                + "\") default method to this interface or a parent interface.",
            typeElement);
        valid = false;
      }
    }
    return valid;
  }

  private void processMethod(
      ExecutableElement method,
      GraphqlQuery queryAnnotation,
      GraphQLSchema graphqlSchema,
      TypeDefinitionRegistry registry,
      TypeGenerator generator,
      QueryValidator validator,
      boolean generateTypes,
      String targetPackage) {

    var queryString = queryAnnotation.value();
    Document document;
    try {
      document = Parser.parse(queryString);
    } catch (Exception e) {
      messager.printMessage(
          Diagnostic.Kind.ERROR, "Failed to parse GraphQL query: " + e.getMessage(), method);
      return;
    }

    if (!validator.validate(graphqlSchema, document, method) || !generateTypes) {
      return;
    }

    var operation = findOperation(document);
    if (operation == null) {
      messager.printMessage(
          Diagnostic.Kind.ERROR, "No operation definition found in GraphQL query", method);
      return;
    }

    var returnTypeName = getSimpleTypeName(method.getReturnType());
    if (returnTypeName != null && !isExistingExternalType(method.getReturnType(), targetPackage)) {
      var rootType = getRootType(operation, registry);
      if (rootType != null) {
        var rootField = findRootField(operation.getSelectionSet());
        if (rootField != null && rootField.getSelectionSet() != null) {
          var rootFieldDef = findFieldDefinition(rootType, rootField.getName());
          if (rootFieldDef != null) {
            var fieldTypeName = unwrapTypeName(rootFieldDef.getType());
            var fieldObjectType =
                registry.getType(fieldTypeName, ObjectTypeDefinition.class).orElse(null);
            if (fieldObjectType != null) {
              generator.generateResultType(
                  returnTypeName, rootField.getSelectionSet(), fieldObjectType, method);
            }
          }
        }
      }
    }

    var params = method.getParameters();
    var variableDefs = operation.getVariableDefinitions();

    for (var param : params) {
      var paramTypeName = getSimpleTypeName(param.asType());
      if (paramTypeName == null || isJavaBuiltIn(paramTypeName)) {
        continue;
      }

      if (isExistingExternalType(param.asType(), targetPackage)) {
        continue;
      }

      var graphqlInputTypeName = findGraphqlInputType(paramTypeName, variableDefs);
      if (graphqlInputTypeName != null) {
        generator.generateInputType(paramTypeName, graphqlInputTypeName, method);
      }
    }
  }

  private OperationDefinition findOperation(Document document) {
    for (var def : document.getDefinitions()) {
      if (def instanceof OperationDefinition definition) {
        return definition;
      }
    }
    return null;
  }

  private Field findRootField(SelectionSet selectionSet) {
    if (selectionSet == null) {
      return null;
    }
    for (var selection : selectionSet.getSelections()) {
      if (selection instanceof Field field) {
        return field;
      }
    }
    return null;
  }

  private FieldDefinition findFieldDefinition(ObjectTypeDefinition typeDef, String fieldName) {
    for (var fd : typeDef.getFieldDefinitions()) {
      if (fd.getName().equals(fieldName)) {
        return fd;
      }
    }
    return null;
  }

  private ObjectTypeDefinition getRootType(
      OperationDefinition operation, TypeDefinitionRegistry registry) {
    var rootTypeName =
        switch (operation.getOperation()) {
          case MUTATION ->
              registry
                  .schemaDefinition()
                  .flatMap(
                      sd ->
                          sd.getOperationTypeDefinitions().stream()
                              .filter(otd -> otd.getName().equals("mutation"))
                              .findFirst())
                  .map(otd -> otd.getTypeName().getName())
                  .orElse("Mutation");
          case SUBSCRIPTION ->
              registry
                  .schemaDefinition()
                  .flatMap(
                      sd ->
                          sd.getOperationTypeDefinitions().stream()
                              .filter(otd -> otd.getName().equals("subscription"))
                              .findFirst())
                  .map(otd -> otd.getTypeName().getName())
                  .orElse("Subscription");
          default ->
              registry
                  .schemaDefinition()
                  .flatMap(
                      sd ->
                          sd.getOperationTypeDefinitions().stream()
                              .filter(otd -> otd.getName().equals("query"))
                              .findFirst())
                  .map(otd -> otd.getTypeName().getName())
                  .orElse("Query");
        };
    return registry.getType(rootTypeName, ObjectTypeDefinition.class).orElse(null);
  }

  private String findGraphqlInputType(
      String javaParamTypeName, List<VariableDefinition> variableDefs) {
    for (var varDef : variableDefs) {
      var graphqlTypeName = unwrapTypeName(varDef.getType());
      if (graphqlTypeName.equals(javaParamTypeName)) {
        return graphqlTypeName;
      }
    }
    return javaParamTypeName;
  }

  private String unwrapTypeName(Type<?> type) {
    if (type instanceof NonNullType nullType) {
      return unwrapTypeName(nullType.getType());
    }
    if (type instanceof ListType listType) {
      return unwrapTypeName(listType.getType());
    }
    if (type instanceof graphql.language.TypeName name) {
      return name.getName();
    }
    return "String";
  }

  private static final Set<String> JAVA_BUILT_INS =
      Set.of(
          "String",
          "Integer",
          "Long",
          "Double",
          "Float",
          "Boolean",
          "Object",
          "Byte",
          "Short",
          "Character",
          "BigDecimal",
          "BigInteger");

  private boolean isJavaBuiltIn(String typeName) {
    return JAVA_BUILT_INS.contains(typeName);
  }

  private String getSimpleTypeName(TypeMirror typeMirror) {
    if (typeMirror instanceof DeclaredType declaredType) {
      var typeElement = declaredType.asElement();
      var simpleName = typeElement.getSimpleName().toString();

      if ("List".equals(simpleName)) {
        var typeArgs = declaredType.getTypeArguments();
        if (!typeArgs.isEmpty()) {
          return getSimpleTypeName(typeArgs.get(0));
        }
      }

      return simpleName;
    }
    return null;
  }

  private boolean isExistingExternalType(TypeMirror typeMirror, String targetPackage) {
    var unwrapped = unwrapListTypeMirror(typeMirror);
    if (unwrapped.getKind() == TypeKind.ERROR) {
      return false;
    }
    if (unwrapped instanceof DeclaredType type) {
      var element = type.asElement();
      if (element instanceof TypeElement typeElement) {
        var qualifiedName = typeElement.getQualifiedName().toString();
        var lastDot = qualifiedName.lastIndexOf('.');
        var typePkg = lastDot > 0 ? qualifiedName.substring(0, lastDot) : "";
        return !typePkg.equals(targetPackage);
      }
    }
    return false;
  }

  private TypeMirror unwrapListTypeMirror(TypeMirror typeMirror) {
    if (typeMirror instanceof DeclaredType declaredType) {
      var simpleName = declaredType.asElement().getSimpleName().toString();
      if ("List".equals(simpleName)) {
        var typeArgs = declaredType.getTypeArguments();
        if (!typeArgs.isEmpty()) {
          return typeArgs.get(0);
        }
      }
    }
    return typeMirror;
  }

  private String getPackageName(TypeElement typeElement) {
    var enclosing = typeElement.getEnclosingElement();
    while (enclosing != null && !(enclosing instanceof PackageElement)) {
      enclosing = enclosing.getEnclosingElement();
    }
    if (enclosing instanceof PackageElement element) {
      return element.getQualifiedName().toString();
    }
    return "";
  }
}
