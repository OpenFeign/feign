/**
 * Copyright 2012-2018 The Feign Authors
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
package feign.jackson.jaxb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import static com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider.DEFAULT_ANNOTATIONS;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;

public final class JacksonJaxbJsonEncoder implements Encoder {
  private final JacksonJaxbJsonProvider jacksonJaxbJsonProvider;

  public JacksonJaxbJsonEncoder() {
    this.jacksonJaxbJsonProvider = new JacksonJaxbJsonProvider();
  }

  public JacksonJaxbJsonEncoder(ObjectMapper objectMapper) {
    this.jacksonJaxbJsonProvider = new JacksonJaxbJsonProvider(objectMapper, DEFAULT_ANNOTATIONS);
  }


  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template)
      throws EncodeException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try {
      jacksonJaxbJsonProvider.writeTo(object, bodyType.getClass(), null, null,
          APPLICATION_JSON_TYPE, null, outputStream);
      template.body(outputStream.toByteArray(), Charset.defaultCharset());
    } catch (IOException e) {
      throw new EncodeException(e.getMessage(), e);
    }
  }
}
