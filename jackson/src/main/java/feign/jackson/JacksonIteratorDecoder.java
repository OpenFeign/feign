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
package feign.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.RuntimeJsonMappingException;
import feign.Response;
import feign.Util;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Iterator;
import static feign.Util.ensureClosed;

/**
 * Jackson decoder which return a closeable iterator. Returned iterator auto-close the
 * {@code Response} when it reached json array end or failed to parse stream. If this iterator is
 * not fetched till the end, it has to be casted to {@code Closeable} and explicity
 * {@code Closeable#close} by the consumer.
 * <p>
 * <p>
 * <p>
 * Example: <br>
 * 
 * <pre>
 * <code>
 * Feign.builder()
 *   .decoder(JacksonIteratorDecoder.create())
 *   .doNotCloseAfterDecode() // Required to fetch the iterator after the response is processed, need to be close
 *   .target(GitHub.class, "https://api.github.com");
 * interface GitHub {
 *  {@literal @}RequestLine("GET /repos/{owner}/{repo}/contributors")
 *   Iterator<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);
 * }</code>
 * </pre>
 */
public final class JacksonIteratorDecoder implements Decoder {

  private final ObjectMapper mapper;

  JacksonIteratorDecoder(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Object decode(Response response, Type type) throws IOException {
    if (response.status() == 404)
      return Util.emptyValueOf(type);
    if (response.body() == null)
      return null;
    Reader reader = response.body().asReader();
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
      return new JacksonIterator<Object>(actualIteratorTypeArgument(type), mapper, response,
          reader);
    } catch (RuntimeJsonMappingException e) {
      if (e.getCause() != null && e.getCause() instanceof IOException) {
        throw IOException.class.cast(e.getCause());
      }
      throw e;
    }
  }

  private static Type actualIteratorTypeArgument(Type type) {
    if (!(type instanceof ParameterizedType)) {
      throw new IllegalArgumentException("Not supported type " + type.toString());
    }
    ParameterizedType parameterizedType = (ParameterizedType) type;
    if (!Iterator.class.equals(parameterizedType.getRawType())) {
      throw new IllegalArgumentException(
          "Not an iterator type " + parameterizedType.getRawType().toString());
    }
    return ((ParameterizedType) type).getActualTypeArguments()[0];
  }

  public static JacksonIteratorDecoder create() {
    return create(Collections.<Module>emptyList());
  }

  public static JacksonIteratorDecoder create(Iterable<Module> modules) {
    return new JacksonIteratorDecoder(new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .registerModules(modules));
  }

  public static JacksonIteratorDecoder create(ObjectMapper objectMapper) {
    return new JacksonIteratorDecoder(objectMapper);
  }

  static final class JacksonIterator<T> implements Iterator<T>, Closeable {
    private final Response response;
    private final JsonParser parser;
    private final ObjectReader objectReader;

    private T current;

    JacksonIterator(Type type, ObjectMapper mapper, Response response, Reader reader)
        throws IOException {
      this.response = response;
      this.parser = mapper.getFactory().createParser(reader);
      this.objectReader = mapper.reader().forType(mapper.constructType(type));
    }

    @Override
    public boolean hasNext() {
      try {
        JsonToken jsonToken = parser.nextToken();
        if (jsonToken == null) {
          return false;
        }

        if (jsonToken == JsonToken.START_ARRAY) {
          jsonToken = parser.nextToken();
        }

        if (jsonToken == JsonToken.END_ARRAY) {
          current = null;
          ensureClosed(this);
          return false;
        }

        current = objectReader.readValue(parser);
      } catch (IOException e) {
        // Input Stream closed automatically by parser
        throw new DecodeException(e.getMessage(), e);
      }
      return current != null;
    }

    @Override
    public T next() {
      return current;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws IOException {
      ensureClosed(this.response);
    }
  }
}
