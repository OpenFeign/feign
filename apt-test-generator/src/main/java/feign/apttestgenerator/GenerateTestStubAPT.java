/**
 * Copyright 2012-2019 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.apttestgenerator;

import com.google.auto.service.AutoService;
import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes({
    "feign.RequestLine"
})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public class GenerateTestStubAPT extends AbstractProcessor {

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    System.out.println(annotations);
    System.out.println(roundEnv);

    final Map<TypeElement, List<ExecutableElement>> clientsToGenerate = annotations.stream()
        .map(roundEnv::getElementsAnnotatedWith)
        .flatMap(Set::stream)
        .map(ExecutableElement.class::cast)
        .collect(Collectors.toMap(
            annotatedMethod -> TypeElement.class.cast(annotatedMethod.getEnclosingElement()),
            ImmutableList::of,
            (list1, list2) -> ImmutableList.<ExecutableElement>builder()
                .addAll(list1)
                .addAll(list2)
                .build()));

    System.out.println("Count: " + clientsToGenerate.size());
    System.out.println("clientsToGenerate: " + clientsToGenerate);

    clientsToGenerate.forEach((type, methods) -> {
      try {
        final String jPackage = readPackage(type);
        final String stubName = type.getSimpleName() + "Stub";
        final JavaFileObject builderFile = processingEnv.getFiler()
            .createSourceFile(stubName);
        final StringBuilder writer = new StringBuilder();
        writer.append("package " + jPackage + ";").append("\n");
        writer.append("import java.util.concurrent.atomic.AtomicInteger;").append("\n");
        writer.append("public class " + stubName).append("\n");
        writer.append("    implements ").append(type);
        writer.append(" {").append("\n");

        methods.stream()
            .forEach(method -> {
              final String methodName = method.getSimpleName().toString();
              final String privateName = "method_" + methods.indexOf(method);

              // method to verify invocation cound
              writer
                  .append("protected final AtomicInteger __invocation_count_")
                  .append(privateName)
                  .append(" = new AtomicInteger(0);")
                  .append("\n");
              writer
                  .append("public int ")
                  .append(methodName)
                  .append("InvocationCount() {\n")
                  .append("return __invocation_count_")
                  .append(privateName)
                  .append(".get();\n")
                  .append("}\n");


              // method that allows a mocked result to be set
              if (method.getReturnType().getKind() != TypeKind.VOID) {
                writer
                    .append("protected ")
                    .append(method.getReturnType())
                    .append(" __answer_")
                    .append(privateName)
                    .append(";")
                    .append("\n");
                final Converter<String, String> upperCase =
                    CaseFormat.LOWER_CAMEL.converterTo(CaseFormat.UPPER_CAMEL);
                writer
                    .append("public ")
                    .append(stubName)
                    .append(" with")
                    .append(upperCase.convert(methodName))
                    .append("(")
                    .append(method.getReturnType())
                    .append(" arg) {")
                    .append("\n")
                    .append("this.__answer_")
                    .append(privateName)
                    .append("= arg;\n")
                    .append("return this;\n")
                    .append("}\n");
              }

              // actual method implementation
              writer
                  .append("@Override\n")
                  .append("public ")
                  .append(method.getReturnType())
                  .append(" ")
                  .append(methodName)
                  .append("(");
              writer.append(
                  method.getParameters()
                      .stream()
                      .map(variable -> variable.asType() + " " + variable.getSimpleName())
                      .collect(Collectors.joining(", ")));
              writer
                  .append(")")
                  .append("{\n");
              writer
                  .append("__invocation_count_")
                  .append(privateName)
                  .append(".incrementAndGet();\n");

              if (method.getReturnType().getKind() != TypeKind.VOID) {
                writer
                    .append("return this.__answer_")
                    .append(privateName)
                    .append(";")
                    .append("\n");
              }

              writer
                  .append("}")
                  .append("\n");
            });

        writer.append("}").append("\n");

        System.out.println(writer);

        builderFile.openWriter().append(writer).close();
      } catch (final Exception e) {
        e.printStackTrace();
        processingEnv.getMessager().printMessage(Kind.ERROR,
            "Unable to generate factory for " + type);
      }
    });

    return true;
  }



  private Type toJavaType(TypeMirror type) {
    outType(type.getClass());
    if (type instanceof WildcardType) {

    }
    return Object.class;
  }

  private void outType(Class<?> class1) {
    if (Object.class.equals(class1) || class1 == null) {
      return;
    }
    System.out.println(class1);
    outType(class1.getSuperclass());
    Arrays.stream(class1.getInterfaces()).forEach(this::outType);
  }



  private String readPackage(Element type) {
    if (type.getKind() == ElementKind.PACKAGE) {
      return type.toString();
    }

    if (type.getKind() == ElementKind.CLASS
        || type.getKind() == ElementKind.INTERFACE) {
      return readPackage(type.getEnclosingElement());
    }

    return null;
  }

}
