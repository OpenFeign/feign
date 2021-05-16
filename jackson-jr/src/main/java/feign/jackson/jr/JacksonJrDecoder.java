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

import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.jr.ob.JSONObjectException;
import com.fasterxml.jackson.jr.ob.JacksonJrExtension;
import feign.Response;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * A {@link Decoder} that uses Jackson Jr to convert objects to String or byte representation.
 */
public class JacksonJrDecoder extends JacksonJrMapper implements Decoder {

  @FunctionalInterface
  interface Transformer {
    Object apply(JSON mapper, Reader reader) throws IOException;
  }

  public JacksonJrDecoder() {
    super();
  }

  /**
   * Construct with a custom {@link JSON} to use for decoding
   * 
   * @param mapper the mapper to use
   */
  public JacksonJrDecoder(JSON mapper) {
    super(mapper);
  }

  /**
   * Construct with a series of {@link JacksonJrExtension} objects that are registered into the
   * {@link JSON}
   * 
   * @param iterable the source of the extensions
   */
  public JacksonJrDecoder(Iterable<JacksonJrExtension> iterable) {
    super(iterable);
  }

  @Override
  public Object decode(Response response, Type type) throws IOException {

    Transformer transformer = findTransformer(response, type);

    if (response.body() == null) {
      return null;
    }
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
      return transformer.apply(mapper, reader);
    } catch (JSONObjectException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      }
      throw e;
    }
  }

  private static Transformer findTransformer(Response response, Type type) {
    if (type instanceof Class) {
      return (mapper, reader) -> mapper.beanFrom((Class<?>) type, reader);
    }
    if (type instanceof ParameterizedType) {
      Type rawType = ((ParameterizedType) type).getRawType();
      Type[] parameterType = ((ParameterizedType) type).getActualTypeArguments();
      if (rawType.equals(List.class)) {
        return (mapper, reader) -> mapper.listOfFrom((Class<?>) parameterType[0], reader);
      }
      if (rawType.equals(Map.class)) {
        return (mapper, reader) -> mapper.mapOfFrom((Class<?>) parameterType[1], reader);
      }
    }
    throw new DecodeException(500, "Cannot decode type: " + type.getTypeName(), response.request());
  }
}
