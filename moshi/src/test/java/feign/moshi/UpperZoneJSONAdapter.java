/*
 * Copyright 2012-2023 The Feign Authors
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
package feign.moshi;

import com.squareup.moshi.*;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;

class UpperZoneJSONAdapter extends JsonAdapter<LinkedList<Zone>> {

  @ToJson
  public void toJson(JsonWriter out, LinkedList<Zone> value) throws IOException {
    out.beginArray();
    for (Zone zone : value) {
      out.beginObject();
      for (Map.Entry<String, Object> entry : zone.entrySet()) {
        out.name(entry.getKey()).value(entry.getValue().toString().toUpperCase());
      }
      out.endObject();
    }
    out.endArray();
  }

  @FromJson
  public LinkedList<Zone> fromJson(JsonReader in) throws IOException {
    LinkedList<Zone> zones = new LinkedList<>();
    in.beginArray();
    while (in.hasNext()) {
      in.beginObject();
      Zone zone = new Zone();
      while (in.hasNext()) {
        zone.put(in.nextName(), in.nextString().toUpperCase());
      }
      in.endObject();
      zones.add(zone);
    }
    in.endArray();
    return zones;
  }
}
