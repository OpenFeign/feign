/**
 * Copyright 2012-2020 The Feign Authors
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

import com.github.jknack.handlebars.*;
import com.github.jknack.handlebars.context.FieldValueResolver;
import com.github.jknack.handlebars.context.JavaBeanValueResolver;
import com.github.jknack.handlebars.context.MapValueResolver;
import com.github.jknack.handlebars.io.URLTemplateSource;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import java.io.IOError;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes({
    "feign.RequestLine"
})
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

    final Handlebars handlebars = new Handlebars();

    final URLTemplateSource source =
        new URLTemplateSource("stub.mustache", getClass().getResource("/stub.mustache"));
    Template template;
    try {
      template = handlebars.with(EscapingStrategy.JS).compile(source);
    } catch (final IOException e) {
      throw new IOError(e);
    }


    clientsToGenerate.forEach((type, executables) -> {
      try {
        final String jPackage = readPackage(type);
        final String className = type.getSimpleName().toString();
        final JavaFileObject builderFile = processingEnv.getFiler()
            .createSourceFile(jPackage + "." + className + "Stub");

        final ClientDefinition client = new ClientDefinition(
            jPackage,
            className,
            type.toString());

        final List<MethodDefinition> methods = executables.stream()
            .map(method -> {
              final String methodName = method.getSimpleName().toString();

              final List<ArgumentDefinition> args = method.getParameters()
                  .stream()
                  .map(var -> new ArgumentDefinition(var.getSimpleName().toString(),
                      var.asType().toString()))
                  .collect(Collectors.toList());
              return new MethodDefinition(
                  methodName,
                  method.getReturnType().toString(),
                  method.getReturnType().getKind() == TypeKind.VOID,
                  args);
            })
            .collect(Collectors.toList());

        final Context context = Context.newBuilder(template)
            .combine("client", client)
            .combine("methods", methods)
            .resolver(JavaBeanValueResolver.INSTANCE, MapValueResolver.INSTANCE,
                FieldValueResolver.INSTANCE)
            .build();
        final String stubSource = template.apply(context);
        System.out.println(stubSource);

        builderFile.openWriter().append(stubSource).close();
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

