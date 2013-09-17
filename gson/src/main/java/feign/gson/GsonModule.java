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

import static feign.Util.ensureClosed;
import static feign.Util.resolveLastTypeParameter;

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
import feign.Feign;
import feign.RequestTemplate;
import feign.Response;
import feign.codec.Decoder;
import feign.codec.Encoder;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 *
 *
 * <h3>Custom type adapters</h3>
 *
 * <br>
 * In order to specify custom json parsing, {@code Gson} supports {@link TypeAdapter type adapters}.
 * This module adds one to read numbers in a {@code Map<String, Object>} as Integers. You can
 * customize further by adding additional set bindings to the raw type {@code TypeAdapter}. <br>
 * Here's an example of adding a custom json type adapter.
 *
 * <pre>
 * &#064;Provides(type = Provides.Type.SET)
 * TypeAdapter upperZone() {
 *     return new TypeAdapter&lt;Zone&gt;() {
 *
 *         &#064;Override
 *         public void write(JsonWriter out, Zone value) throws IOException {
 *             throw new IllegalArgumentException();
 *         }
 *
 *         &#064;Override
 *         public Zone read(JsonReader in) throws IOException {
 *             in.beginObject();
 *             Zone zone = new Zone();
 *             while (in.hasNext()) {
 *                 zone.put(in.nextName(), in.nextString().toUpperCase());
 *             }
 *             in.endObject();
 *             return zone;
 *         }
 *     };
 * }
 * </pre>
 */
@dagger.Module(injects = Feign.class, addsTo = Feign.Defaults.class)
public final class GsonModule {

  @Provides
  Encoder encoder(GsonCodec codec) {
    return codec;
  }

  @Provides
  Decoder decoder(GsonCodec codec) {
    return codec;
  }

  static class GsonCodec implements Encoder, Decoder {
    private final Gson gson;

    @Inject
    GsonCodec(Gson gson) {
      this.gson = gson;
    }

    @Override
    public void encode(Object object, RequestTemplate template) {
      template.body(gson.toJson(object));
    }

    @Override
    public Object decode(Response response, Type type) throws IOException {
      if (void.class.equals(type) || response.body() == null) {
        return null;
      }
      Reader reader = response.body().asReader();
      try {
        return fromJson(new JsonReader(reader), type);
      } finally {
        ensureClosed(reader);
      }
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

  @Provides
  @Singleton
  Gson gson(Set<TypeAdapter> adapters) {
    GsonBuilder builder = new GsonBuilder().setPrettyPrinting();
    for (TypeAdapter<?> adapter : adapters) {
      Type type = resolveLastTypeParameter(adapter.getClass(), TypeAdapter.class);
      builder.registerTypeAdapter(type, adapter);
    }
    return builder.create();
  }

  // deals with scenario where gson Object type treats all numbers as doubles.
  @Provides(type = Provides.Type.SET)
  TypeAdapter doubleToInt() {
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

  private static final TypeToken<Map<String, Object>> token =
      new TypeToken<Map<String, Object>>() {};
}
