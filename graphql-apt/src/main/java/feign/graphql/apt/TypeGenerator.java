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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  private final Map<String, ResultTypeUsage> resultTypeSignatures = new HashMap<>();
  private TypeAnnotationConfig annotationConfig = TypeAnnotationConfig.EMPTY;

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

  public void setAnnotationConfig(TypeAnnotationConfig config) {
    this.annotationConfig = config;
  }

  public void generateResultType(
      String className,
      SelectionSet selectionSet,
      ObjectTypeDefinition parentType,
      Element element) {
    var signature = canonicalize(selectionSet);
    var fields = describeFields(selectionSet);
    var existing = resultTypeSignatures.get(className);
    if (existing != null) {
      if (!existing.signature.equals(signature)) {
        messager.printMessage(
            Diagnostic.Kind.ERROR,
            "Conflicting return type '"
                + className
                + "': method selects ["
                + fields
                + "] but method '"
                + existing.element.getSimpleName()
                + "()' already selects ["
                + existing.fields
                + "]",
            element);
        messager.printMessage(
            Diagnostic.Kind.ERROR,
            "Conflicting return type '"
                + className
                + "': method selects ["
                + existing.fields
                + "] but method '"
                + element.getSimpleName()
                + "()' selects ["
                + fields
                + "]",
            existing.element);
        return;
      }
      return;
    }
    resultTypeSignatures.put(className, new ResultTypeUsage(signature, fields, element));

    var tree = buildResultType(className, selectionSet, parentType, "");
    if (tree == null) {
      return;
    }

    writeResultRecord(tree, element);
    processPendingTypes(element);
  }

  private ResultTypeDefinition buildResultType(
      String className,
      SelectionSet selectionSet,
      ObjectTypeDefinition parentType,
      String pathPrefix) {
    var fields = new ArrayList<RecordField>();
    var innerTypes = new ArrayList<ResultTypeDefinition>();

    for (var selection : selectionSet.getSelections()) {
      if (!(selection instanceof Field field)) {
        continue;
      }
      var fieldName = field.getName();
      var schemaDef = findFieldDefinition(parentType, fieldName);
      if (schemaDef == null) {
        continue;
      }

      var fieldType = schemaDef.getType();
      var rawTypeName = unwrapTypeName(fieldType);

      if (field.getSelectionSet() != null && !field.getSelectionSet().getSelections().isEmpty()) {
        var nestedClassName = capitalize(fieldName);
        var nestedPath = pathPrefix.isEmpty() ? fieldName : pathPrefix + "." + fieldName;
        var nestedObjectType =
            registry.getType(rawTypeName, ObjectTypeDefinition.class).orElse(null);
        if (nestedObjectType != null) {
          var innerTree =
              buildResultType(
                  nestedClassName, field.getSelectionSet(), nestedObjectType, nestedPath);
          if (innerTree != null) {
            innerTypes.add(innerTree);
          }
        }
        var nestedType =
            wrapType(fieldType, ClassName.get("", nestedClassName), annotationConfig.useOptional());
        var fieldNonNull = fieldType instanceof NonNullType;
        fields.add(toRecordField(fieldName, nestedType, fieldNonNull));
      } else {
        var javaType = typeMapper.map(fieldType, annotationConfig.useOptional());
        var fieldNonNull = fieldType instanceof NonNullType;
        fields.add(toRecordField(fieldName, javaType, fieldNonNull));
        enqueueIfNonScalar(rawTypeName);
      }
    }

    return new ResultTypeDefinition(className, pathPrefix, fields, innerTypes);
  }

  private void writeResultRecord(ResultTypeDefinition tree, Element element) {
    var fqn = targetPackage.isEmpty() ? tree.className : targetPackage + "." + tree.className;
    try {
      var sourceFile = filer.createSourceFile(fqn, element);
      try (var out = new PrintWriter(sourceFile.openWriter())) {
        if (!targetPackage.isEmpty()) {
          out.println("package " + targetPackage + ";");
          out.println();
        }

        var imports = new TreeSet<String>();
        collectAllImports(tree, imports);
        imports.addAll(annotationConfig.imports());
        if (!imports.isEmpty()) {
          for (var imp : imports) {
            out.println("import " + imp + ";");
          }
          out.println();
        }

        writeRecordBody(out, tree, "");
      }
    } catch (FilerException e) {
      // Type already generated by another interface in the same compilation round
    } catch (IOException e) {
      messager.printMessage(
          Diagnostic.Kind.ERROR,
          "Failed to write generated type " + tree.className + ": " + e.getMessage(),
          element);
    }
  }

  private void writeRecordBody(PrintWriter out, ResultTypeDefinition tree, String indent) {
    var pathPrefix = tree.fieldName;
    var params =
        tree.fields.stream()
            .map(f -> formatFieldParam(f, pathPrefix))
            .collect(Collectors.joining(", "));

    for (var annotation : annotationConfig.annotations()) {
      out.println(indent + annotation);
    }

    if (tree.innerTypes.isEmpty()) {
      out.println(indent + "public record " + tree.className + "(" + params + ") {}");
    } else {
      out.println(indent + "public record " + tree.className + "(" + params + ") {");
      out.println();
      for (var inner : tree.innerTypes) {
        writeRecordBody(out, inner, indent + "  ");
        out.println();
      }
      out.println(indent + "}");
    }
  }

  private void collectAllImports(ResultTypeDefinition tree, Set<String> imports) {
    for (var field : tree.fields) {
      if (field.typeName != null) {
        collectImportsFromTypeName(field.typeName, imports);
      }
    }
    for (var inner : tree.innerTypes) {
      collectAllImports(inner, imports);
    }
  }

  private String canonicalize(SelectionSet selectionSet) {
    var entries = new ArrayList<String>();
    for (var selection : selectionSet.getSelections()) {
      if (!(selection instanceof Field field)) {
        continue;
      }
      var name = field.getName();
      if (field.getSelectionSet() != null && !field.getSelectionSet().getSelections().isEmpty()) {
        entries.add(name + "{" + canonicalize(field.getSelectionSet()) + "}");
      } else {
        entries.add(name);
      }
    }
    entries.sort(String::compareTo);
    return String.join(",", entries);
  }

  private String describeFields(SelectionSet selectionSet) {
    var entries = new ArrayList<String>();
    for (var selection : selectionSet.getSelections()) {
      if (!(selection instanceof Field field)) {
        continue;
      }
      var name = field.getName();
      if (field.getSelectionSet() != null && !field.getSelectionSet().getSelections().isEmpty()) {
        entries.add(name + " { " + describeFields(field.getSelectionSet()) + " }");
      } else {
        entries.add(name);
      }
    }
    return String.join(", ", entries);
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
      var javaType = typeMapper.map(fieldType, annotationConfig.useOptional());
      var fieldNonNull = fieldType instanceof NonNullType;
      fields.add(toRecordField(fieldName, javaType, fieldNonNull));

      var rawTypeName = unwrapTypeName(fieldType);
      enqueueIfNonScalar(rawTypeName);
    }

    writeRecord(className, fields, element);
    processPendingTypes(element);
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
      var fieldType = fieldDef.getType();
      var javaType = typeMapper.map(fieldType, annotationConfig.useOptional());
      var fieldNonNull = fieldType instanceof NonNullType;
      fields.add(toRecordField(fieldName, javaType, fieldNonNull));

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

  private TypeName wrapType(Type<?> schemaType, TypeName innerType, boolean useOptional) {
    boolean nullable = !(schemaType instanceof NonNullType);
    var wrapped = wrapTypeInner(schemaType, innerType);
    if (useOptional && nullable) {
      return ParameterizedTypeName.get(ClassName.get(Optional.class), wrapped);
    }
    return wrapped;
  }

  private TypeName wrapTypeInner(Type<?> schemaType, TypeName innerType) {
    if (schemaType instanceof NonNullType type) {
      return wrapTypeInner(type.getType(), innerType);
    }
    if (schemaType instanceof ListType type) {
      return ParameterizedTypeName.get(
          ClassName.get(List.class), wrapTypeInner(type.getType(), innerType));
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

  private String formatFieldParam(RecordField f, String pathPrefix) {
    var fieldPath = pathPrefix.isEmpty() ? f.name : pathPrefix + "." + f.name;
    var fa = annotationConfig.fieldAnnotations().get(fieldPath);
    var hasNonNull = f.nonNull && !annotationConfig.nonNullAnnotations().isEmpty();

    var typeStr = fa != null && fa.typeOverride() != null ? fa.typeOverride() : f.typeString;

    if (!hasNonNull && (fa == null || fa.annotations().isEmpty())) {
      return typeStr + " " + f.name;
    }

    var fieldAnns = new ArrayList<String>();
    if (hasNonNull) {
      fieldAnns.addAll(annotationConfig.nonNullAnnotations());
    }
    if (fa != null) {
      fieldAnns.addAll(fa.annotations());
    }
    return String.join(" ", fieldAnns) + " " + typeStr + " " + f.name;
  }

  private RecordField toRecordField(String name, TypeName typeName, boolean nonNull) {
    var typeString = typeNameToString(typeName);
    return new RecordField(typeString, name, typeName, nonNull);
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
        imports.addAll(annotationConfig.imports());
        if (!imports.isEmpty()) {
          for (var imp : imports) {
            out.println("import " + imp + ";");
          }
          out.println();
        }

        for (var annotation : annotationConfig.annotations()) {
          out.println(annotation);
        }

        var params =
            fields.stream().map(f -> formatFieldParam(f, "")).collect(Collectors.joining(", "));

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

  record ResultTypeUsage(String signature, String fields, Element element) {}

  record ResultTypeDefinition(
      String className,
      String fieldName,
      List<RecordField> fields,
      List<ResultTypeDefinition> innerTypes) {}

  record RecordField(String typeString, String name, TypeName typeName, boolean nonNull) {}
}
