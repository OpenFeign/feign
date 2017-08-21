/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import java.io.IOException;
import java.util.Iterator;

import feign.Response;
import feign.codec.DecodeException;

public class JacksonIterator<T> implements Iterator<T> {
  private final JsonParser parser;
  private final ObjectReader reader;
  private T current;

  public JacksonIterator(final Class<T> type, final ObjectMapper mapper, final Response response)
      throws IOException {
    this(mapper.getFactory().createParser(response.body().asInputStream()),
        mapper.reader().forType(type));
  }

  public JacksonIterator(final JsonParser parser, final ObjectReader reader) {
    this.parser = parser;
    this.reader = reader;
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
      current = reader.readValue(parser);
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

  public static class Builder<T> {
    private Class<T> type;
    private ObjectMapper mapper;
    private Response response;

    public Builder<T> of(Class<?> type) {
      this.type = (Class<T>) type; // FIXME Convenient but not safe, should we allow ?
      return this;
    }

    public Builder<T> mapper(ObjectMapper mapper) {
      this.mapper = mapper;
      return this;
    }

    public Builder<T> response(Response response) {
      this.response = response;
      return this;
    }

    public JacksonIterator<T> build() {
      try {
        return new JacksonIterator<T>(type, mapper, response);
      } catch (IOException e) {
        throw new DecodeException(e.getMessage(), e);
      }
    }
  }

  public static <T> Builder<T> builder() {
    return new Builder<T>();
  }
}
