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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
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
 * Jackson decoder which return a closeable iterator.
 * Iterator <b>shall</b> be closed by the consumer.
 * <p>
 * <p>
 * <p>Example: <br>
 * <pre><code>
 * Feign.builder()
 *   .decoder(new JacksonIteratorDecoder())
 *   .closeAfterDecode(false) // Required to fetch the iterator after the response is processed, need to be close
 *   .target(GitHub.class, "https://api.github.com");
 * interface GitHub {
 *  {@literal @}RequestLine("GET /repos/{owner}/{repo}/contributors")
 *   Iterator<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);
 * }</code></pre>
 *
 * @author Pierrick HYMBERT
 */
public class JacksonIteratorDecoder implements Decoder {

  private final ObjectMapper mapper;

  public JacksonIteratorDecoder() {
    this(Collections.<Module>emptyList());
  }

  public JacksonIteratorDecoder(Iterable<Module> modules) {
    this(new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .registerModules(modules));
  }

  public JacksonIteratorDecoder(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Object decode(Response response, Type type) throws IOException {
    if (response.status() == 404) return Util.emptyValueOf(type);
    if (response.body() == null) return null;
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
      return new JacksonIterator<Object>(actualIteratorTypeArgument(type), mapper, reader);
    } catch (RuntimeJsonMappingException e) {
      if (e.getCause() != null && e.getCause() instanceof IOException) {
        throw IOException.class.cast(e.getCause());
      }
      throw e;
    }
  }

  private Type actualIteratorTypeArgument(Type type) {
    if (!(type instanceof ParameterizedType)) {
      throw new IllegalArgumentException("Not supported type " + type.toString());
    }
    ParameterizedType parameterizedType = (ParameterizedType) type;
    if (!Iterator.class.equals(parameterizedType.getRawType())) {
      throw new IllegalArgumentException("Not supported an iterator " + parameterizedType.getRawType().toString());
    }
    return ((ParameterizedType) type).getActualTypeArguments()[0];
  }

  private final class JacksonIterator<T> implements Iterator<T>, Closeable {
    private final Reader reader;
    private final JsonParser parser;
    private final ObjectReader objectReader;

    private T current;

    private JacksonIterator(final Type type, final ObjectMapper mapper, final Reader reader)
        throws IOException {
      this.reader = reader;
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
        if (JsonToken.START_ARRAY.equals(jsonToken)) {
          jsonToken = parser.nextToken();
        }
        if (JsonToken.END_ARRAY.equals(jsonToken)) {
          current = null;
          return false;
        }
        if (!JsonToken.START_OBJECT.equals(jsonToken)) {
          throw new IOException("Unexpected json token in response body " + jsonToken);
        }
        current = objectReader.readValue(parser);
      } catch (IOException e) {
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
      ensureClosed(this.reader);
    }
  }
}
