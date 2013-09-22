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

import static feign.Util.resolveLastTypeParameter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import dagger.Provides;
import feign.Feign;
import feign.codec.Decoder;
import feign.codec.Encoder;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Set;
import javax.inject.Singleton;

/**
 *
 *
 * <h3>Custom type adapters</h3>
 *
 * <br>
 * In order to specify custom json parsing, {@code Gson} supports {@link TypeAdapter type adapters}.
 * This module adds one to read numbers in a {@code Map<String, Object>} as Integers. You can
 * customize further by adding additional set bindings to the raw type {@code TypeAdapter}.
 *
 * <p><br>
 * Here's an example of adding a custom json type adapter.
 *
 * <p>
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
  Encoder encoder(Gson gson) {
    return new GsonEncoder(gson);
  }

  @Provides
  Decoder decoder(Gson gson) {
    return new GsonDecoder(gson);
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

  @Provides(type = Provides.Type.SET_VALUES)
  Set<TypeAdapter> noDefaultTypeAdapters() {
    return Collections.emptySet();
  }
}
