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
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.SelectionSet;
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
import javax.lang.model.element.Element;
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
    for (Element element : roundEnv.getElementsAnnotatedWith(GraphqlSchema.class)) {
      if (!(element instanceof TypeElement)) {
        continue;
      }
      processInterface((TypeElement) element);
    }
    return true;
  }

  private void processInterface(TypeElement typeElement) {
    GraphqlSchema schemaAnnotation = typeElement.getAnnotation(GraphqlSchema.class);
    String schemaPath = schemaAnnotation.value();

    SchemaLoader loader = new SchemaLoader(filer, messager);
    String schemaContent = loader.load(schemaPath, typeElement);
    if (schemaContent == null) {
      return;
    }

    SchemaParser schemaParser = new SchemaParser();
    TypeDefinitionRegistry registry;
    try {
      registry = schemaParser.parse(schemaContent);
    } catch (Exception e) {
      messager.printMessage(
          Diagnostic.Kind.ERROR, "Failed to parse GraphQL schema: " + e.getMessage(), typeElement);
      return;
    }

    Map<String, TypeName> customScalars = collectScalarMappings(typeElement);

    if (!validateCustomScalars(registry, customScalars, typeElement)) {
      return;
    }

    GraphQLSchema graphqlSchema = UnExecutableSchemaGenerator.makeUnExecutableSchema(registry);

    boolean generateTypes = schemaAnnotation.generateTypes();

    String targetPackage = getPackageName(typeElement);
    GraphqlTypeMapper typeMapper = new GraphqlTypeMapper(targetPackage, customScalars);
    QueryValidator validator = new QueryValidator(messager);
    TypeGenerator generator =
        new TypeGenerator(filer, messager, registry, typeMapper, targetPackage);

    for (Element enclosed : typeElement.getEnclosedElements()) {
      if (!(enclosed instanceof ExecutableElement)) {
        continue;
      }
      ExecutableElement method = (ExecutableElement) enclosed;
      GraphqlQuery queryAnnotation = method.getAnnotation(GraphqlQuery.class);
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
    Map<String, TypeName> scalars = new HashMap<>();
    collectScalarsFromType(typeElement, scalars);
    collectScalarsFromParents(typeElement, scalars);
    return scalars;
  }

  private void collectScalarsFromType(TypeElement typeElement, Map<String, TypeName> scalars) {
    for (Element enclosed : typeElement.getEnclosedElements()) {
      if (!(enclosed instanceof ExecutableElement)) {
        continue;
      }
      ExecutableElement method = (ExecutableElement) enclosed;
      Scalar scalarAnnotation = method.getAnnotation(Scalar.class);
      if (scalarAnnotation == null) {
        continue;
      }
      String scalarName = scalarAnnotation.value();
      TypeName javaType = TypeName.get(method.getReturnType());
      scalars.put(scalarName, javaType);
    }
  }

  private void collectScalarsFromParents(TypeElement typeElement, Map<String, TypeName> scalars) {
    for (TypeMirror iface : typeElement.getInterfaces()) {
      if (iface instanceof DeclaredType) {
        Element element = ((DeclaredType) iface).asElement();
        if (element instanceof TypeElement) {
          TypeElement parentType = (TypeElement) element;
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
    Map<String, ScalarTypeDefinition> schemaScalars = registry.scalars();
    boolean valid = true;
    for (String scalarName : schemaScalars.keySet()) {
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

    String queryString = queryAnnotation.value();
    Document document;
    try {
      document = Parser.parse(queryString);
    } catch (Exception e) {
      messager.printMessage(
          Diagnostic.Kind.ERROR, "Failed to parse GraphQL query: " + e.getMessage(), method);
      return;
    }

    if (!validator.validate(graphqlSchema, document, method)) {
      return;
    }

    if (!generateTypes) {
      return;
    }

    OperationDefinition operation = findOperation(document);
    if (operation == null) {
      messager.printMessage(
          Diagnostic.Kind.ERROR, "No operation definition found in GraphQL query", method);
      return;
    }

    String returnTypeName = getSimpleTypeName(method.getReturnType());
    if (returnTypeName != null && !isExistingExternalType(method.getReturnType(), targetPackage)) {
      graphql.language.ObjectTypeDefinition rootType = getRootType(operation, registry);
      if (rootType != null) {
        graphql.language.Field rootField = findRootField(operation.getSelectionSet());
        if (rootField != null && rootField.getSelectionSet() != null) {
          graphql.language.FieldDefinition rootFieldDef =
              findFieldDefinition(rootType, rootField.getName());
          if (rootFieldDef != null) {
            String fieldTypeName = unwrapTypeName(rootFieldDef.getType());
            graphql.language.ObjectTypeDefinition fieldObjectType =
                registry
                    .getType(fieldTypeName, graphql.language.ObjectTypeDefinition.class)
                    .orElse(null);
            if (fieldObjectType != null) {
              generator.generateResultType(
                  returnTypeName, rootField.getSelectionSet(), fieldObjectType, method);
            }
          }
        }
      }
    }

    List<? extends javax.lang.model.element.VariableElement> params = method.getParameters();
    List<VariableDefinition> variableDefs = operation.getVariableDefinitions();

    for (javax.lang.model.element.VariableElement param : params) {
      String paramTypeName = getSimpleTypeName(param.asType());
      if (paramTypeName == null || isJavaBuiltIn(paramTypeName)) {
        continue;
      }

      if (isExistingExternalType(param.asType(), targetPackage)) {
        continue;
      }

      String graphqlInputTypeName = findGraphqlInputType(paramTypeName, variableDefs);
      if (graphqlInputTypeName != null) {
        generator.generateInputType(paramTypeName, graphqlInputTypeName, method);
      }
    }
  }

  private OperationDefinition findOperation(Document document) {
    for (Definition<?> def : document.getDefinitions()) {
      if (def instanceof OperationDefinition) {
        return (OperationDefinition) def;
      }
    }
    return null;
  }

  private graphql.language.Field findRootField(SelectionSet selectionSet) {
    if (selectionSet == null) {
      return null;
    }
    for (graphql.language.Selection<?> selection : selectionSet.getSelections()) {
      if (selection instanceof graphql.language.Field) {
        return (graphql.language.Field) selection;
      }
    }
    return null;
  }

  private graphql.language.FieldDefinition findFieldDefinition(
      graphql.language.ObjectTypeDefinition typeDef, String fieldName) {
    for (graphql.language.FieldDefinition fd : typeDef.getFieldDefinitions()) {
      if (fd.getName().equals(fieldName)) {
        return fd;
      }
    }
    return null;
  }

  private graphql.language.ObjectTypeDefinition getRootType(
      OperationDefinition operation, TypeDefinitionRegistry registry) {
    String rootTypeName;
    switch (operation.getOperation()) {
      case MUTATION:
        rootTypeName =
            registry
                .schemaDefinition()
                .flatMap(
                    sd ->
                        sd.getOperationTypeDefinitions().stream()
                            .filter(otd -> otd.getName().equals("mutation"))
                            .findFirst())
                .map(otd -> otd.getTypeName().getName())
                .orElse("Mutation");
        break;
      case SUBSCRIPTION:
        rootTypeName =
            registry
                .schemaDefinition()
                .flatMap(
                    sd ->
                        sd.getOperationTypeDefinitions().stream()
                            .filter(otd -> otd.getName().equals("subscription"))
                            .findFirst())
                .map(otd -> otd.getTypeName().getName())
                .orElse("Subscription");
        break;
      default:
        rootTypeName =
            registry
                .schemaDefinition()
                .flatMap(
                    sd ->
                        sd.getOperationTypeDefinitions().stream()
                            .filter(otd -> otd.getName().equals("query"))
                            .findFirst())
                .map(otd -> otd.getTypeName().getName())
                .orElse("Query");
        break;
    }
    return registry.getType(rootTypeName, graphql.language.ObjectTypeDefinition.class).orElse(null);
  }

  private String findGraphqlInputType(
      String javaParamTypeName, List<VariableDefinition> variableDefs) {
    for (VariableDefinition varDef : variableDefs) {
      String graphqlTypeName = unwrapTypeName(varDef.getType());
      if (graphqlTypeName.equals(javaParamTypeName)) {
        return graphqlTypeName;
      }
    }
    return javaParamTypeName;
  }

  private String unwrapTypeName(graphql.language.Type<?> type) {
    if (type instanceof graphql.language.NonNullType) {
      return unwrapTypeName(((graphql.language.NonNullType) type).getType());
    }
    if (type instanceof graphql.language.ListType) {
      return unwrapTypeName(((graphql.language.ListType) type).getType());
    }
    if (type instanceof graphql.language.TypeName) {
      return ((graphql.language.TypeName) type).getName();
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
    if (typeMirror instanceof DeclaredType) {
      DeclaredType declaredType = (DeclaredType) typeMirror;
      Element typeElement = declaredType.asElement();
      String simpleName = typeElement.getSimpleName().toString();

      if ("List".equals(simpleName)) {
        List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
        if (!typeArgs.isEmpty()) {
          return getSimpleTypeName(typeArgs.get(0));
        }
      }

      return simpleName;
    }
    return null;
  }

  private boolean isExistingExternalType(TypeMirror typeMirror, String targetPackage) {
    TypeMirror unwrapped = unwrapListTypeMirror(typeMirror);
    if (unwrapped.getKind() == TypeKind.ERROR) {
      return false;
    }
    if (unwrapped instanceof DeclaredType) {
      Element element = ((DeclaredType) unwrapped).asElement();
      if (element instanceof TypeElement) {
        String qualifiedName = ((TypeElement) element).getQualifiedName().toString();
        int lastDot = qualifiedName.lastIndexOf('.');
        String typePkg = lastDot > 0 ? qualifiedName.substring(0, lastDot) : "";
        return !typePkg.equals(targetPackage);
      }
    }
    return false;
  }

  private TypeMirror unwrapListTypeMirror(TypeMirror typeMirror) {
    if (typeMirror instanceof DeclaredType) {
      DeclaredType declaredType = (DeclaredType) typeMirror;
      String simpleName = declaredType.asElement().getSimpleName().toString();
      if ("List".equals(simpleName)) {
        List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
        if (!typeArgs.isEmpty()) {
          return typeArgs.get(0);
        }
      }
    }
    return typeMirror;
  }

  private String getPackageName(TypeElement typeElement) {
    Element enclosing = typeElement.getEnclosingElement();
    while (enclosing != null && !(enclosing instanceof PackageElement)) {
      enclosing = enclosing.getEnclosingElement();
    }
    if (enclosing instanceof PackageElement) {
      return ((PackageElement) enclosing).getQualifiedName().toString();
    }
    return "";
  }
}
