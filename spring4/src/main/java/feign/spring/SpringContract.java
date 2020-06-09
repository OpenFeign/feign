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
package feign.spring;

import java.util.ArrayList;
import java.util.Collection;
import org.springframework.web.bind.annotation.*;
import feign.DeclarativeContract;
import feign.MethodMetadata;
import feign.Request;

public class SpringContract extends DeclarativeContract {

  static final String ACCEPT = "Accept";
  static final String CONTENT_TYPE = "Content-Type";

  public SpringContract() {
    registerClassAnnotation(RequestMapping.class, (requestMapping, data) -> {
      appendMappings(data, requestMapping.value());

      if (requestMapping.method().length == 1)
        data.template().method(Request.HttpMethod.valueOf(requestMapping.method()[0].name()));

      handleProducesAnnotation(data, requestMapping.produces());
      handleConsumesAnnotation(data, requestMapping.consumes());
    });

    registerMethodAnnotation(RequestMapping.class, (requestMapping, data) -> {
      String[] mappings = requestMapping.value();
      appendMappings(data, mappings);

      if (requestMapping.method().length == 1)
        data.template().method(Request.HttpMethod.valueOf(requestMapping.method()[0].name()));
    });


    registerMethodAnnotation(GetMapping.class, (mapping, data) -> {
      appendMappings(data, mapping.value());
      data.template().method(Request.HttpMethod.GET);
      handleProducesAnnotation(data, mapping.produces());
      handleConsumesAnnotation(data, mapping.consumes());
    });

    registerMethodAnnotation(PostMapping.class, (mapping, data) -> {
      appendMappings(data, mapping.value());
      data.template().method(Request.HttpMethod.POST);
      handleProducesAnnotation(data, mapping.produces());
      handleConsumesAnnotation(data, mapping.consumes());
    });

    registerMethodAnnotation(PutMapping.class, (mapping, data) -> {
      appendMappings(data, mapping.value());
      data.template().method(Request.HttpMethod.PUT);
      handleProducesAnnotation(data, mapping.produces());
      handleConsumesAnnotation(data, mapping.consumes());
    });

    registerMethodAnnotation(DeleteMapping.class, (mapping, data) -> {
      appendMappings(data, mapping.value());
      data.template().method(Request.HttpMethod.DELETE);
      handleProducesAnnotation(data, mapping.produces());
      handleConsumesAnnotation(data, mapping.consumes());
    });

    registerMethodAnnotation(PatchMapping.class, (mapping, data) -> {
      appendMappings(data, mapping.value());
      data.template().method(Request.HttpMethod.PATCH);
      handleProducesAnnotation(data, mapping.produces());
      handleConsumesAnnotation(data, mapping.consumes());
    });

    registerMethodAnnotation(ResponseBody.class, (body, data) -> {
      handleConsumesAnnotation(data, "application/json");
    });
    registerMethodAnnotation(ExceptionHandler.class, (ann, data) -> {
      data.ignoreMethod();
    });
    registerParameterAnnotation(PathVariable.class, (parameterAnnotation, data, paramIndex) -> {
      String name = PathVariable.class.cast(parameterAnnotation).value();
      nameParam(data, name, paramIndex);
    });

    registerParameterAnnotation(RequestBody.class, (body, data, paramIndex) -> {
      handleProducesAnnotation(data, "application/json");
    });
    registerParameterAnnotation(RequestParam.class, (parameterAnnotation, data, paramIndex) -> {
      String name = RequestParam.class.cast(parameterAnnotation).value();
      Collection<String> query = addTemplatedParam(data.template().queries().get(name), name);
      data.template().query(name, query);
      nameParam(data, name, paramIndex);
    });

  }

  private void appendMappings(MethodMetadata data, String[] mappings) {
    for (int i = 0; i < mappings.length; i++) {
      String methodAnnotationValue = mappings[i];
      if (!methodAnnotationValue.startsWith("/") && !data.template().url().endsWith("/")) {
        methodAnnotationValue = "/" + methodAnnotationValue;
      }
      if (data.template().url().endsWith("/") && methodAnnotationValue.startsWith("/")) {
        methodAnnotationValue = methodAnnotationValue.substring(1);
      }

      data.template().uri(data.template().url() + methodAnnotationValue);
    }
  }

  private void handleProducesAnnotation(MethodMetadata data, String... produces) {
    if (produces.length == 0)
      return;
    data.template().removeHeader(ACCEPT); // remove any previous produces
    data.template().header(ACCEPT, produces[0]);
  }

  private void handleConsumesAnnotation(MethodMetadata data, String... consumes) {
    if (consumes.length == 0)
      return;
    data.template().removeHeader(CONTENT_TYPE); // remove any previous consumes
    data.template().header(CONTENT_TYPE, consumes[0]);
  }

  protected Collection<String> addTemplatedParam(Collection<String> possiblyNull, String name) {
    if (possiblyNull == null) {
      possiblyNull = new ArrayList<String>();
    }
    possiblyNull.add(String.format("{%s}", name));
    return possiblyNull;
  }

}
