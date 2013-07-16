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
package feign.gson;

import static dagger.Provides.Type.SET;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import com.google.gson.JsonIOException;
import com.google.gson.TypeAdapter;
import com.google.gson.internal.ConstructorConstructor;
import com.google.gson.internal.bind.MapTypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dagger.Provides;
import feign.IncrementalCallback;
import feign.codec.Decoder;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.codec.IncrementalDecoder;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

@dagger.Module(library = true, overrides = true)
public final class GsonModule {

  @Provides(type = SET)
  Encoder encoder(GsonCodec codec) {
    return codec;
  }

  @Provides(type = SET)
  Decoder decoder(GsonCodec codec) {
    return codec;
  }

  @Provides(type = SET)
  IncrementalDecoder incrementalDecoder(GsonCodec codec) {
    return codec;
  }

  static class GsonCodec
      implements Encoder.Text<Object>,
          Decoder.TextStream<Object>,
          IncrementalDecoder.TextStream<Object> {
    private final Gson gson;

    @Inject
    GsonCodec(Gson gson) {
      this.gson = gson;
    }

    @Override
    public String encode(Object object) throws EncodeException {
      return gson.toJson(object);
    }

    @Override
    public Object decode(Reader reader, Type type) throws IOException {
      return fromJson(new JsonReader(reader), type);
    }

    @Override
    public void decode(
        Reader reader, Type type, IncrementalCallback<? super Object> incrementalCallback)
        throws IOException {
      JsonReader jsonReader = new JsonReader(reader);
      jsonReader.beginArray();
      while (jsonReader.hasNext()) {
        incrementalCallback.onNext(fromJson(jsonReader, type));
      }
      jsonReader.endArray();
    }

    private Object fromJson(JsonReader jsonReader, Type type) throws IOException {
      try {
        return gson.fromJson(jsonReader, type);
      } catch (JsonIOException e) {
        if (e.getCause() != null && e.getCause() instanceof IOException) {
          throw IOException.class.cast(e.getCause());
        }
        throw e;
      }
    }
  }

  // deals with scenario where gson Object type treats all numbers as doubles.
  @Provides
  TypeAdapter<Map<String, Object>> doubleToInt() {
    return new TypeAdapter<Map<String, Object>>() {
      TypeAdapter<Map<String, Object>> delegate =
          new MapTypeAdapterFactory(
                  new ConstructorConstructor(Collections.<Type, InstanceCreator<?>>emptyMap()),
                  false)
              .create(new Gson(), token);

      @Override
      public void write(JsonWriter out, Map<String, Object> value) throws IOException {
        delegate.write(out, value);
      }

      @Override
      public Map<String, Object> read(JsonReader in) throws IOException {
        Map<String, Object> map = delegate.read(in);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
          if (entry.getValue() instanceof Double) {
            entry.setValue(Double.class.cast(entry.getValue()).intValue());
          }
        }
        return map;
      }
    }.nullSafe();
  }

  @Provides
  @Singleton
  Gson gson(TypeAdapter<Map<String, Object>> doubleToInt) {
    return new GsonBuilder()
        .registerTypeAdapter(token.getType(), doubleToInt)
        .setPrettyPrinting()
        .create();
  }

  protected static final TypeToken<Map<String, Object>> token =
      new TypeToken<Map<String, Object>>() {};
}
