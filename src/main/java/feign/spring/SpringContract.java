package feign.spring;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import feign.Contract.BaseContract;
import feign.MethodMetadata;

public class SpringContract extends BaseContract
{

  static final String ACCEPT = "Accept";
  static final String CONTENT_TYPE = "Content-Type";

  protected void processAnnotationOnClass(MethodMetadata data, Class<?> targetType)
  {
    if (targetType.isAnnotationPresent(RequestMapping.class))
    {
      RequestMapping requestMapping = targetType.getAnnotation(RequestMapping.class);
      appendMappings(data, requestMapping.value());

      if (requestMapping.method().length == 1)
        data.template().method(requestMapping.method()[0].name());

      handleProducesAnnotation(data, requestMapping.produces());
      handleConsumesAnnotation(data, requestMapping.consumes());

    }
  }

  private void appendMappings(MethodMetadata data, String[] mappings)
  {
    for (int i = 0; i < mappings.length; i++)
    {
      String mapping = mappings[i];
      if (data.template().url().length() != 0 && !data.template().url().endsWith("/") && !mapping.startsWith("/"))
        data.template().append("/");

      data.template().append(mapping);
    }
  }

  @Override
  protected void processAnnotationOnMethod(MethodMetadata data, Annotation annotation, Method method)
  {
    if (annotation.annotationType() == RequestMapping.class)
    {
      RequestMapping requestMapping = RequestMapping.class.cast(annotation);
      String[] mappings = requestMapping.value();
      appendMappings(data, mappings);

      if (requestMapping.method().length == 1)
        data.template().method(requestMapping.method()[0].name());

    }

    if (annotation.annotationType() == ResponseBody.class)
      handleConsumesAnnotation(data, "application/json");

    if (annotation.annotationType() == ExceptionHandler.class)
      data.template().method("CONFIG");
  }

  private void handleProducesAnnotation(MethodMetadata data, String... produces)
  {
    if (produces.length == 0)
      return;
    data.template().header(ACCEPT, (String) null); // remove any previous
                                                   // produces
    data.template().header(ACCEPT, produces[0]);
  }

  private void handleConsumesAnnotation(MethodMetadata data, String... consumes)
  {
    if (consumes.length == 0)
      return;
    data.template().header(CONTENT_TYPE, (String) null); // remove any previous
                                                         // consumes
    data.template().header(CONTENT_TYPE, consumes[0]);
  }

  @Override
  protected boolean processAnnotationsOnParameter(MethodMetadata data, Annotation[] annotations, int paramIndex)
  {
    boolean isHttpParam = false;
    for (Annotation parameterAnnotation : annotations)
    {
      Class<? extends Annotation> annotationType = parameterAnnotation.annotationType();
      if (annotationType == PathVariable.class)
      {
        String name = PathVariable.class.cast(parameterAnnotation).value();
        nameParam(data, name, paramIndex);
        isHttpParam = true;
      }

      if (annotationType == RequestBody.class)
        handleProducesAnnotation(data, "application/json");

      if (annotationType == RequestParam.class)
      {
        String name = RequestParam.class.cast(parameterAnnotation).value();
        Collection<String> query = addTemplatedParam(data.template().queries().get(name), name);
        data.template().query(name, query);
        nameParam(data, name, paramIndex);
        isHttpParam = true;
      }
    }
    return isHttpParam;
  }

  protected Collection<String> addTemplatedParam(Collection<String> possiblyNull, String name) {
    if (possiblyNull == null) {
      possiblyNull = new ArrayList<String>();
    }
    possiblyNull.add(String.format("{%s}", name));
    return possiblyNull;
  }

}
