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
package feign.jackson3;

import com.fasterxml.jackson.annotation.JsonInclude;
import feign.RequestTemplate;
import feign.Util;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import java.lang.reflect.Type;
import java.util.Collections;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

public class Jackson3Encoder implements Encoder {

  private final JsonMapper mapper;

  public Jackson3Encoder() {
    this(Collections.<JacksonModule>emptyList());
  }

  public Jackson3Encoder(Iterable<JacksonModule> modules) {
    this(
        JsonMapper.builder()
            .changeDefaultPropertyInclusion(
                incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
            .enable(SerializationFeature.INDENT_OUTPUT)
            .addModules(modules)
            .build());
  }

  public Jackson3Encoder(JsonMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template) {
    try {
      JavaType javaType = mapper.getTypeFactory().constructType(bodyType);
      template.body(mapper.writerFor(javaType).writeValueAsBytes(object), Util.UTF_8);
    } catch (JacksonException e) {
      throw new EncodeException(e.getMessage(), e);
    }
  }
}
