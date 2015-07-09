/*
 * Copyright 2013 Netflix, Inc.
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
package feign.jackson;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.RuntimeJsonMappingException;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Collections;

import feign.Response;
import feign.codec.Decoder;

public class JacksonDecoder implements Decoder {

  private final ObjectMapper mapper;

  public JacksonDecoder() {
    this(Collections.<Module>emptyList());
  }

  public JacksonDecoder(Iterable<Module> modules) {
    this(new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
             .registerModules(modules));
  }

  public JacksonDecoder(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Object decode(Response response, Type type) throws IOException {
    if (response.body() == null) {
      return null;
    }
    InputStream inputStream = response.body().asInputStream();
    try {
      if (inputStream.available() <= 0){
        return null;
      }
      return mapper.readValue(inputStream, mapper.constructType(type));
    } catch (RuntimeJsonMappingException e) {
      if (e.getCause() != null && e.getCause() instanceof IOException) {
        throw IOException.class.cast(e.getCause());
      }
      throw e;
    }
  }
}
