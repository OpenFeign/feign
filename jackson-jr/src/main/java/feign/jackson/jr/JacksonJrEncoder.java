/**
 * Copyright 2012-2021 The Feign Authors
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
package feign.jackson.jr;

import java.io.IOException;
import java.lang.reflect.Type;
import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.jr.ob.JacksonJrExtension;
import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import static java.lang.String.format;

/**
 * A {@link Encoder} that uses Jackson Jr to convert objects to String or byte representation.
 */
public class JacksonJrEncoder extends JacksonJrMapper implements Encoder {

  public JacksonJrEncoder() {
    super();
  }

  /**
   * Construct with a custom {@link JSON} to use for encoding
   * 
   * @param mapper the mapper to use
   */
  public JacksonJrEncoder(JSON mapper) {
    super(mapper);
  }

  /**
   * Construct with a series of {@link JacksonJrExtension} objects that are registered into the
   * {@link JSON}
   * 
   * @param iterable the source of the extensions
   */
  public JacksonJrEncoder(Iterable<JacksonJrExtension> iterable) {
    super(iterable);
  }

  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template) {
    try {
      if (bodyType == byte[].class) {
        template.body(mapper.asBytes(object), null);
      } else {
        template.body(mapper.asString(object));
      }
    } catch (IOException e) {
      throw new EncodeException(e.getMessage(), e);
    }
  }
}
