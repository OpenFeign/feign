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
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValueDefinition;
import graphql.language.Field;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.SelectionSet;
import graphql.language.Type;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import javax.annotation.processing.Filer;
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

    TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(className).addModifiers(Modifier.PUBLIC);

    for (graphql.language.Selection<?> selection : selectionSet.getSelections()) {
      if (!(selection instanceof Field)) {
        continue;
      }
      Field field = (Field) selection;
      String fieldName = field.getName();

      FieldDefinition schemaDef = findFieldDefinition(parentType, fieldName);
      if (schemaDef == null) {
        continue;
      }

      Type<?> fieldType = schemaDef.getType();
      String rawTypeName = unwrapTypeName(fieldType);

      if (field.getSelectionSet() != null && !field.getSelectionSet().getSelections().isEmpty()) {
        String nestedClassName = capitalize(fieldName);
        generateNestedResultType(nestedClassName, field.getSelectionSet(), rawTypeName, element);
        TypeName nestedType = wrapType(fieldType, ClassName.get(targetPackage, nestedClassName));
        addFieldWithAccessors(typeBuilder, fieldName, nestedType);
      } else {
        TypeName javaType = typeMapper.map(fieldType);
        addFieldWithAccessors(typeBuilder, fieldName, javaType);
        enqueueIfNonScalar(rawTypeName);
      }
    }

    writeType(typeBuilder.build(), element);
    processPendingTypes(element);
  }

  public void generateInputType(String className, String graphqlTypeName, Element element) {
    if (generatedTypes.contains(className)) {
      return;
    }
    generatedTypes.add(className);

    Optional<InputObjectTypeDefinition> maybeDef =
        registry.getType(graphqlTypeName, InputObjectTypeDefinition.class);
    if (!maybeDef.isPresent()) {
      messager.printMessage(
          Diagnostic.Kind.ERROR, "GraphQL input type not found: " + graphqlTypeName, element);
      return;
    }

    InputObjectTypeDefinition inputDef = maybeDef.get();
    TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(className).addModifiers(Modifier.PUBLIC);

    for (InputValueDefinition valueDef : inputDef.getInputValueDefinitions()) {
      String fieldName = valueDef.getName();
      Type<?> fieldType = valueDef.getType();
      TypeName javaType = typeMapper.map(fieldType);
      addFieldWithAccessors(typeBuilder, fieldName, javaType);

      String rawTypeName = unwrapTypeName(fieldType);
      enqueueIfNonScalar(rawTypeName);
    }

    writeType(typeBuilder.build(), element);
    processPendingTypes(element);
  }

  private void generateNestedResultType(
      String className, SelectionSet selectionSet, String graphqlTypeName, Element element) {
    if (generatedTypes.contains(className)) {
      return;
    }

    Optional<ObjectTypeDefinition> maybeDef =
        registry.getType(graphqlTypeName, ObjectTypeDefinition.class);
    if (!maybeDef.isPresent()) {
      return;
    }

    generateResultType(className, selectionSet, maybeDef.get(), element);
  }

  private void processPendingTypes(Element element) {
    while (!pendingTypes.isEmpty()) {
      String typeName = pendingTypes.poll();
      if (generatedTypes.contains(typeName)) {
        continue;
      }

      Optional<EnumTypeDefinition> enumDef = registry.getType(typeName, EnumTypeDefinition.class);
      if (enumDef.isPresent()) {
        generateEnum(typeName, enumDef.get(), element);
        continue;
      }

      Optional<InputObjectTypeDefinition> inputDef =
          registry.getType(typeName, InputObjectTypeDefinition.class);
      if (inputDef.isPresent()) {
        generateInputType(typeName, typeName, element);
        continue;
      }

      Optional<ObjectTypeDefinition> objectDef =
          registry.getType(typeName, ObjectTypeDefinition.class);
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

    TypeSpec.Builder enumBuilder = TypeSpec.enumBuilder(className).addModifiers(Modifier.PUBLIC);

    for (EnumValueDefinition value : enumDef.getEnumValueDefinitions()) {
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

    TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(className).addModifiers(Modifier.PUBLIC);

    for (FieldDefinition fieldDef : objectDef.getFieldDefinitions()) {
      String fieldName = fieldDef.getName();
      TypeName javaType = typeMapper.map(fieldDef.getType());
      addFieldWithAccessors(typeBuilder, fieldName, javaType);

      String rawTypeName = unwrapTypeName(fieldDef.getType());
      enqueueIfNonScalar(rawTypeName);
    }

    writeType(typeBuilder.build(), element);
    processPendingTypes(element);
  }

  private void enqueueIfNonScalar(String typeName) {
    if (!typeMapper.isScalar(typeName) && !generatedTypes.contains(typeName)) {
      pendingTypes.add(typeName);
    }
  }

  private FieldDefinition findFieldDefinition(ObjectTypeDefinition typeDef, String fieldName) {
    for (FieldDefinition fd : typeDef.getFieldDefinitions()) {
      if (fd.getName().equals(fieldName)) {
        return fd;
      }
    }
    return null;
  }

  private String unwrapTypeName(Type<?> type) {
    if (type instanceof NonNullType) {
      return unwrapTypeName(((NonNullType) type).getType());
    }
    if (type instanceof ListType) {
      return unwrapTypeName(((ListType) type).getType());
    }
    if (type instanceof graphql.language.TypeName) {
      return ((graphql.language.TypeName) type).getName();
    }
    return "String";
  }

  private TypeName wrapType(Type<?> schemaType, TypeName innerType) {
    if (schemaType instanceof NonNullType) {
      return wrapType(((NonNullType) schemaType).getType(), innerType);
    }
    if (schemaType instanceof ListType) {
      return com.squareup.javapoet.ParameterizedTypeName.get(
          ClassName.get(List.class), wrapType(((ListType) schemaType).getType(), innerType));
    }
    return innerType;
  }

  private void addFieldWithAccessors(TypeSpec.Builder typeBuilder, String name, TypeName type) {
    FieldSpec field = FieldSpec.builder(type, name, Modifier.PRIVATE).build();
    typeBuilder.addField(field);

    typeBuilder.addMethod(
        MethodSpec.methodBuilder("get" + capitalize(name))
            .addModifiers(Modifier.PUBLIC)
            .returns(type)
            .addStatement("return $N", name)
            .build());

    typeBuilder.addMethod(
        MethodSpec.methodBuilder("set" + capitalize(name))
            .addModifiers(Modifier.PUBLIC)
            .addParameter(type, name)
            .addStatement("this.$N = $N", name, name)
            .build());
  }

  private String capitalize(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  private void writeType(TypeSpec typeSpec, Element element) {
    try {
      JavaFile javaFile = JavaFile.builder(targetPackage, typeSpec).build();
      javaFile.writeTo(filer);
    } catch (IOException e) {
      messager.printMessage(
          Diagnostic.Kind.ERROR,
          "Failed to write generated type " + typeSpec.name + ": " + e.getMessage(),
          element);
    }
  }
}
