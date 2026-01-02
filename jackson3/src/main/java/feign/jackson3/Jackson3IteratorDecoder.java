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

import static feign.Util.UTF_8;
import static feign.Util.ensureClosed;

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
import java.util.NoSuchElementException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 3 decoder which return a closeable iterator. Returned iterator auto-close the {@code
 * Response} when it reached json array end or failed to parse stream. If this iterator is not
 * fetched till the end, it has to be casted to {@code Closeable} and explicity {@code
 * Closeable#close} by the consumer.
 *
 * <p>
 *
 * <p>
 *
 * <p>Example: <br>
 *
 * <pre>
 * <code>
 * Feign.builder()
 *   .decoder(Jackson3IteratorDecoder.create())
 *   .doNotCloseAfterDecode() // Required to fetch the iterator after the response is processed, need to be close
 *   .target(GitHub.class, "https://api.github.com");
 * interface GitHub {
 *  {@literal @}RequestLine("GET /repos/{owner}/{repo}/contributors")
 *   Iterator<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);
 * }</code>
 * </pre>
 */
public final class Jackson3IteratorDecoder implements Decoder {

  private final JsonMapper mapper;

  Jackson3IteratorDecoder(JsonMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Object decode(Response response, Type type) throws IOException {
    if (response.status() == 404 || response.status() == 204) return Util.emptyValueOf(type);
    if (response.body() == null) return null;
    Reader reader = response.body().asReader(UTF_8);
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
      return new Jackson3Iterator<Object>(
          actualIteratorTypeArgument(type), mapper, response, reader);
    } catch (JacksonException e) {
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

  public static Jackson3IteratorDecoder create() {
    return create(Collections.<JacksonModule>emptyList());
  }

  public static Jackson3IteratorDecoder create(Iterable<JacksonModule> modules) {
    return new Jackson3IteratorDecoder(
        JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            // Disable FAIL_ON_TRAILING_TOKENS for iterator: we read a JSON array element by
            // element, so there are always "trailing tokens" (the remaining array elements)
            .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .addModules(modules)
            .build());
  }

  public static Jackson3IteratorDecoder create(JsonMapper jsonMapper) {
    return new Jackson3IteratorDecoder(jsonMapper);
  }

  static final class Jackson3Iterator<T> implements Iterator<T>, Closeable {
    private final Response response;
    private final JsonParser parser;
    private final ObjectReader objectReader;

    private T current;

    Jackson3Iterator(Type type, JsonMapper mapper, Response response, Reader reader)
        throws IOException {
      this.response = response;
      this.parser = mapper.createParser(reader);
      this.objectReader = mapper.reader().forType(mapper.constructType(type));
    }

    @Override
    public boolean hasNext() {
      if (current == null) {
        current = readNext();
      }
      return current != null;
    }

    private T readNext() {
      try {
        JsonToken jsonToken = parser.nextToken();
        if (jsonToken == null) {
          return null;
        }

        if (jsonToken == JsonToken.START_ARRAY) {
          jsonToken = parser.nextToken();
        }

        if (jsonToken == JsonToken.END_ARRAY) {
          ensureClosed(this);
          return null;
        }

        return objectReader.readValue(parser);
      } catch (JacksonException e) {
        // Input Stream closed automatically by parser
        throw new DecodeException(response.status(), e.getMessage(), response.request(), e);
      }
    }

    @Override
    public T next() {
      if (current != null) {
        T tmp = current;
        current = null;
        return tmp;
      }
      T next = readNext();
      if (next == null) {
        throw new NoSuchElementException();
      }
      return next;
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
