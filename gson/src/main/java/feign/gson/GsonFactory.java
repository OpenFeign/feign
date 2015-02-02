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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;

import static feign.Util.resolveLastTypeParameter;

final class GsonFactory {

  private GsonFactory() {
  }

  /**
   * Registers type adapters by implicit type. Adds one to read numbers in a {@code Map<String,
   * Object>} as Integers.
   */
  static Gson create(Iterable<TypeAdapter<?>> adapters) {
    GsonBuilder builder = new GsonBuilder().setPrettyPrinting();
    builder.registerTypeAdapter(new TypeToken<Map<String, Object>>() {
    }.getType(), new DoubleToIntMapTypeAdapter());
    for (TypeAdapter<?> adapter : adapters) {
      Type type = resolveLastTypeParameter(adapter.getClass(), TypeAdapter.class);
      builder.registerTypeAdapter(type, adapter);
    }
    return builder.create();
  }
}
