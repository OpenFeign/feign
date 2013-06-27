package feign;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.net.HttpHeaders.ACCEPT;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URI;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

/** Defines what annotations and values are valid on interfaces. */
public final class Contract {

  public static ImmutableSet<MethodMetadata> parseAndValidatateMetadata(Class<?> declaring) {
    ImmutableSet.Builder<MethodMetadata> builder = ImmutableSet.builder();
    for (Method method : declaring.getDeclaredMethods()) {
      if (method.getDeclaringClass() == Object.class) continue;
      builder.add(parseAndValidatateMetadata(method));
    }
    return builder.build();
  }

  public static MethodMetadata parseAndValidatateMetadata(Method method) {
    MethodMetadata data = new MethodMetadata();
    data.returnType(TypeToken.of(method.getGenericReturnType()));
    data.configKey(Feign.configKey(method));

    for (Annotation methodAnnotation : method.getAnnotations()) {
      Class<? extends Annotation> annotationType = methodAnnotation.annotationType();
      HttpMethod http = annotationType.getAnnotation(HttpMethod.class);
      if (http != null) {
        checkState(
            data.template().method() == null,
            "Method %s contains multiple HTTP methods. Found: %s and %s",
            method.getName(),
            data.template().method(),
            http.value());
        data.template().method(http.value());
      } else if (annotationType == RequestTemplate.Body.class) {
        String body = RequestTemplate.Body.class.cast(methodAnnotation).value();
        if (body.indexOf('{') == -1) {
          data.template().body(body);
        } else {
          data.template().bodyTemplate(body);
        }
      } else if (annotationType == Path.class) {
        data.template().append(Path.class.cast(methodAnnotation).value());
      } else if (annotationType == Produces.class) {
        data.template()
            .header(CONTENT_TYPE, Joiner.on(',').join(((Produces) methodAnnotation).value()));
      } else if (annotationType == Consumes.class) {
        data.template().header(ACCEPT, Joiner.on(',').join(((Consumes) methodAnnotation).value()));
      }
    }
    checkState(
        data.template().method() != null,
        "Method %s not annotated with HTTP method type (ex. GET, POST)",
        method.getName());
    Class<?>[] parameterTypes = method.getParameterTypes();

    Annotation[][] parameterAnnotationArrays = method.getParameterAnnotations();
    int count = parameterAnnotationArrays.length;
    for (int i = 0; i < count; i++) {
      boolean hasHttpAnnotation = false;

      Class<?> parameterType = parameterTypes[i];
      Annotation[] parameterAnnotations = parameterAnnotationArrays[i];
      if (parameterAnnotations != null) {
        for (Annotation parameterAnnotation : parameterAnnotations) {
          Class<? extends Annotation> annotationType = parameterAnnotation.annotationType();
          if (annotationType == PathParam.class) {
            data.indexToName().put(i, PathParam.class.cast(parameterAnnotation).value());
            hasHttpAnnotation = true;
          } else if (annotationType == QueryParam.class) {
            String name = QueryParam.class.cast(parameterAnnotation).value();
            data.template()
                .query(
                    name,
                    ImmutableList.<String>builder()
                        .addAll(data.template().queries().get(name))
                        .add(String.format("{%s}", name))
                        .build());
            data.indexToName().put(i, name);
            hasHttpAnnotation = true;
          } else if (annotationType == HeaderParam.class) {
            String name = HeaderParam.class.cast(parameterAnnotation).value();
            data.template()
                .header(
                    name,
                    ImmutableList.<String>builder()
                        .addAll(data.template().headers().get(name))
                        .add(String.format("{%s}", name))
                        .build());
            data.indexToName().put(i, name);
            hasHttpAnnotation = true;
          } else if (annotationType == FormParam.class) {
            String form = FormParam.class.cast(parameterAnnotation).value();
            data.formParams().add(form);
            data.indexToName().put(i, form);
            hasHttpAnnotation = true;
          }
        }
      }

      if (parameterType == URI.class) {
        data.urlIndex(i);
      } else if (!hasHttpAnnotation) {
        checkState(
            data.formParams().isEmpty(),
            "Body parameters cannot be used with @FormParam parameters.");
        checkState(data.bodyIndex() == null, "Method has too many Body parameters: %s", method);
        data.bodyIndex(i);
      }
    }
    return data;
  }
}
