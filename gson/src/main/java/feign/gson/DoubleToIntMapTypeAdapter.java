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
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.Map;

/**
 * Deals with scenario where Gson Object type treats all numbers as doubles.
 */
public class DoubleToIntMapTypeAdapter extends TypeAdapter<Map<String, Object>> {
  private final TypeAdapter<Map<String, Object>> delegate =
      new Gson().getAdapter(new TypeToken<Map<String, Object>>() {
      });

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
}
