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
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import graphql.language.EnumTypeDefinition;
import graphql.language.Field;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.SelectionSet;
import graphql.language.Type;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;

public class TypeGenerator {

  private final Filer filer;
  private final Messager messager;
  private final TypeDefinitionRegistry registry;
  private final GraphqlTypeMapper typeMapper;
  private final String targetPackage;
  private final Set<String> generatedTypes = new HashSet<>();
  private final Queue<String> pendingTypes = new ArrayDeque<>();

  public TypeGenerator(
      Filer filer,
      Messager messager,
      TypeDefinitionRegistry registry,
      GraphqlTypeMapper typeMapper,
      String targetPackage) {
    this.filer = filer;
    this.messager = messager;
    this.registry = registry;
    this.typeMapper = typeMapper;
    this.targetPackage = targetPackage;
  }

  public void generateResultType(
      String className,
      SelectionSet selectionSet,
      ObjectTypeDefinition parentType,
      Element element) {
    if (generatedTypes.contains(className)) {
      return;
    }
    generatedTypes.add(className);

    var fields = new ArrayList<RecordField>();

    for (var selection : selectionSet.getSelections()) {
      if (!(selection instanceof Field)) {
        continue;
      }
      var field = (Field) selection;
      var fieldName = field.getName();

      var schemaDef = findFieldDefinition(parentType, fieldName);
      if (schemaDef == null) {
        continue;
      }

      var fieldType = schemaDef.getType();
      var rawTypeName = unwrapTypeName(fieldType);

      if (field.getSelectionSet() != null && !field.getSelectionSet().getSelections().isEmpty()) {
        var nestedClassName = capitalize(fieldName);
        generateNestedResultType(nestedClassName, field.getSelectionSet(), rawTypeName, element);
        var nestedType = wrapType(fieldType, ClassName.get(targetPackage, nestedClassName));
        fields.add(toRecordField(fieldName, nestedType));
      } else {
        var javaType = typeMapper.map(fieldType);
        fields.add(toRecordField(fieldName, javaType));
        enqueueIfNonScalar(rawTypeName);
      }
    }

    writeRecord(className, fields, element);
    processPendingTypes(element);
  }

  public void generateInputType(String className, String graphqlTypeName, Element element) {
    if (generatedTypes.contains(className)) {
      return;
    }
    generatedTypes.add(className);

    var maybeDef = registry.getType(graphqlTypeName, InputObjectTypeDefinition.class);
    if (maybeDef.isEmpty()) {
      messager.printMessage(
          Diagnostic.Kind.ERROR, "GraphQL input type not found: " + graphqlTypeName, element);
      return;
    }

    var inputDef = maybeDef.get();
    var fields = new ArrayList<RecordField>();

    for (var valueDef : inputDef.getInputValueDefinitions()) {
      var fieldName = valueDef.getName();
      var fieldType = valueDef.getType();
      var javaType = typeMapper.map(fieldType);
      fields.add(toRecordField(fieldName, javaType));

      var rawTypeName = unwrapTypeName(fieldType);
      enqueueIfNonScalar(rawTypeName);
    }

    writeRecord(className, fields, element);
    processPendingTypes(element);
  }

  private void generateNestedResultType(
      String className, SelectionSet selectionSet, String graphqlTypeName, Element element) {
    if (generatedTypes.contains(className)) {
      return;
    }

    var maybeDef = registry.getType(graphqlTypeName, ObjectTypeDefinition.class);
    if (maybeDef.isEmpty()) {
      return;
    }

    generateResultType(className, selectionSet, maybeDef.get(), element);
  }

  private void processPendingTypes(Element element) {
    while (!pendingTypes.isEmpty()) {
      var typeName = pendingTypes.poll();
      if (generatedTypes.contains(typeName)) {
        continue;
      }

      var enumDef = registry.getType(typeName, EnumTypeDefinition.class);
      if (enumDef.isPresent()) {
        generateEnum(typeName, enumDef.get(), element);
        continue;
      }

      var inputDef = registry.getType(typeName, InputObjectTypeDefinition.class);
      if (inputDef.isPresent()) {
        generateInputType(typeName, typeName, element);
        continue;
      }

      var objectDef = registry.getType(typeName, ObjectTypeDefinition.class);
      if (objectDef.isPresent()) {
        generateFullObjectType(typeName, objectDef.get(), element);
      }
    }
  }

  private void generateEnum(String className, EnumTypeDefinition enumDef, Element element) {
    if (generatedTypes.contains(className)) {
      return;
    }
    generatedTypes.add(className);

    var enumBuilder = TypeSpec.enumBuilder(className).addModifiers(Modifier.PUBLIC);

    for (var value : enumDef.getEnumValueDefinitions()) {
      enumBuilder.addEnumConstant(value.getName());
    }

    writeType(enumBuilder.build(), element);
  }

  private void generateFullObjectType(
      String className, ObjectTypeDefinition objectDef, Element element) {
    if (generatedTypes.contains(className)) {
      return;
    }
    generatedTypes.add(className);

    var fields = new ArrayList<RecordField>();

    for (var fieldDef : objectDef.getFieldDefinitions()) {
      var fieldName = fieldDef.getName();
      var javaType = typeMapper.map(fieldDef.getType());
      fields.add(toRecordField(fieldName, javaType));

      var rawTypeName = unwrapTypeName(fieldDef.getType());
      enqueueIfNonScalar(rawTypeName);
    }

    writeRecord(className, fields, element);
    processPendingTypes(element);
  }

  private void enqueueIfNonScalar(String typeName) {
    if (!typeMapper.isScalar(typeName) && !generatedTypes.contains(typeName)) {
      pendingTypes.add(typeName);
    }
  }

  private FieldDefinition findFieldDefinition(ObjectTypeDefinition typeDef, String fieldName) {
    for (var fd : typeDef.getFieldDefinitions()) {
      if (fd.getName().equals(fieldName)) {
        return fd;
      }
    }
    return null;
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

  private TypeName wrapType(Type<?> schemaType, TypeName innerType) {
    if (schemaType instanceof NonNullType type) {
      return wrapType(type.getType(), innerType);
    }
    if (schemaType instanceof ListType type) {
      return ParameterizedTypeName.get(
          ClassName.get(List.class), wrapType(type.getType(), innerType));
    }
    return innerType;
  }

  private String capitalize(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  private void writeType(TypeSpec typeSpec, Element element) {
    try {
      var javaFile = JavaFile.builder(targetPackage, typeSpec).build();
      javaFile.writeTo(filer);
    } catch (FilerException e) {
      // Type already generated by another interface in the same compilation round
    } catch (IOException e) {
      messager.printMessage(
          Diagnostic.Kind.ERROR,
          "Failed to write generated type " + typeSpec.name + ": " + e.getMessage(),
          element);
    }
  }

  private RecordField toRecordField(String name, TypeName typeName) {
    var typeString = typeNameToString(typeName);
    var importFqn = resolveImport(typeName);
    return new RecordField(typeString, name, importFqn, typeName);
  }

  private String typeNameToString(TypeName typeName) {
    if (typeName instanceof ParameterizedTypeName parameterized) {
      var raw = parameterized.rawType.simpleName();
      var typeArgs =
          parameterized.typeArguments.stream()
              .map(this::typeNameToString)
              .collect(Collectors.joining(", "));
      return raw + "<" + typeArgs + ">";
    }
    if (typeName instanceof ClassName name) {
      return name.simpleName();
    }
    return typeName.toString();
  }

  private String resolveImport(TypeName typeName) {
    if (typeName instanceof ParameterizedTypeName parameterized) {
      resolveImport(parameterized.rawType);
      for (var typeArg : parameterized.typeArguments) {
        resolveImport(typeArg);
      }
      return fqnIfNeeded(parameterized.rawType);
    }
    if (typeName instanceof ClassName name) {
      return fqnIfNeeded(name);
    }
    return null;
  }

  private String fqnIfNeeded(ClassName className) {
    var pkg = className.packageName();
    if (pkg.equals("java.lang") || pkg.equals(targetPackage) || pkg.isEmpty()) {
      return null;
    }
    return pkg + "." + className.simpleName();
  }

  private Set<String> collectImports(List<RecordField> fields) {
    var imports = new TreeSet<String>();
    for (var field : fields) {
      collectImportsFromTypeName(field.typeName, imports);
    }
    return imports;
  }

  private void collectImportsFromTypeName(TypeName typeName, Set<String> imports) {
    if (typeName instanceof ParameterizedTypeName parameterized) {
      collectImportsFromTypeName(parameterized.rawType, imports);
      for (var typeArg : parameterized.typeArguments) {
        collectImportsFromTypeName(typeArg, imports);
      }
    } else if (typeName instanceof ClassName name) {
      var fqn = fqnIfNeeded(name);
      if (fqn != null) {
        imports.add(fqn);
      }
    }
  }

  private void writeRecord(String className, List<RecordField> fields, Element element) {
    var fqn = targetPackage.isEmpty() ? className : targetPackage + "." + className;
    try {
      var sourceFile = filer.createSourceFile(fqn, element);
      try (var out = new PrintWriter(sourceFile.openWriter())) {
        if (!targetPackage.isEmpty()) {
          out.println("package " + targetPackage + ";");
          out.println();
        }

        var imports = collectImports(fields);
        if (!imports.isEmpty()) {
          for (var imp : imports) {
            out.println("import " + imp + ";");
          }
          out.println();
        }

        var params =
            fields.stream().map(f -> f.typeString + " " + f.name).collect(Collectors.joining(", "));

        out.println("public record " + className + "(" + params + ") {}");
      }
    } catch (FilerException e) {
      // Type already generated by another interface in the same compilation round
    } catch (IOException e) {
      messager.printMessage(
          Diagnostic.Kind.ERROR,
          "Failed to write generated type " + className + ": " + e.getMessage(),
          element);
    }
  }

  static class RecordField {
    final String typeString;
    final String name;
    final String importFqn;
    final TypeName typeName;

    RecordField(String typeString, String name, String importFqn) {
      this.typeString = typeString;
      this.name = name;
      this.importFqn = importFqn;
      this.typeName = null;
    }

    RecordField(String typeString, String name, String importFqn, TypeName typeName) {
      this.typeString = typeString;
      this.name = name;
      this.importFqn = importFqn;
      this.typeName = typeName;
    }
  }
}
