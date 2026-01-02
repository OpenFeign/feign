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

import feign.Response;
import feign.Util;
import feign.codec.Decoder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.Collections;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.json.JsonMapper;

public class Jackson3Decoder implements Decoder {

  private final JsonMapper mapper;

  public Jackson3Decoder() {
    this(Collections.<JacksonModule>emptyList());
  }

  public Jackson3Decoder(Iterable<JacksonModule> modules) {
    this(
        JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .addModules(modules)
            .build());
  }

  public Jackson3Decoder(JsonMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Object decode(Response response, Type type) throws IOException {
    if (response.status() == 404 || response.status() == 204) return Util.emptyValueOf(type);
    if (response.body() == null) return null;
    Reader reader = response.body().asReader(response.charset());
    if (!reader.markSupported()) {
      reader = new BufferedReader(reader, 1);
    }
    try {
      // Read the first byte to see if we have any data
      reader.mark(1);
      if (reader.read() == -1) {
        return null; // Eagerly returning null avoids "No content to map due to end-of-input"
      }
      reader.reset();
      return mapper.readValue(reader, mapper.constructType(type));
    } catch (JacksonException e) {
      if (e.getCause() != null && e.getCause() instanceof IOException) {
        throw IOException.class.cast(e.getCause());
      }
      throw e;
    }
  }
}
