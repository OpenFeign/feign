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
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.language.SelectionSet;
import graphql.language.VariableDefinition;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.UnExecutableSchemaGenerator;
import java.util.List;
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
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

@AutoService(Processor.class)
@SupportedAnnotationTypes("feign.graphql.apt.GraphqlSchema")
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

    GraphQLSchema graphqlSchema = UnExecutableSchemaGenerator.makeUnExecutableSchema(registry);

    String targetPackage = getPackageName(typeElement);
    GraphqlTypeMapper typeMapper = new GraphqlTypeMapper(targetPackage);
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

      processMethod(method, queryAnnotation, graphqlSchema, registry, generator, validator);
    }
  }

  private void processMethod(
      ExecutableElement method,
      GraphqlQuery queryAnnotation,
      GraphQLSchema graphqlSchema,
      TypeDefinitionRegistry registry,
      TypeGenerator generator,
      QueryValidator validator) {

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

    OperationDefinition operation = findOperation(document);
    if (operation == null) {
      messager.printMessage(
          Diagnostic.Kind.ERROR, "No operation definition found in GraphQL query", method);
      return;
    }

    String returnTypeName = getSimpleTypeName(method.getReturnType());
    if (returnTypeName != null) {
      SelectionSet selectionSet = operation.getSelectionSet();
      graphql.language.ObjectTypeDefinition rootType = getRootType(operation, registry);
      if (rootType != null && selectionSet != null) {
        generator.generateResultType(returnTypeName, selectionSet, rootType, method);
      }
    }

    List<? extends javax.lang.model.element.VariableElement> params = method.getParameters();
    List<VariableDefinition> variableDefs = operation.getVariableDefinitions();

    for (javax.lang.model.element.VariableElement param : params) {
      String paramTypeName = getSimpleTypeName(param.asType());
      if (paramTypeName == null || isJavaBuiltIn(paramTypeName)) {
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
      return typeElement.getSimpleName().toString();
    }
    return null;
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
