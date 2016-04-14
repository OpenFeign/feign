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

import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import feign.RequestTemplate;
import feign.Response;

import static feign.Util.UTF_8;
import static feign.assertj.FeignAssertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GsonCodecTest {

  @Test
  public void encodesMapObjectNumericalValuesAsInteger() throws Exception {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("foo", 1);

    RequestTemplate template = new RequestTemplate();
    new GsonEncoder().encode(map, map.getClass(), template);

    assertThat(template).hasBody("" //
                                 + "{\n" //
                                 + "  \"foo\": 1\n" //
                                 + "}");
  }

  @Test
  public void decodesMapObjectNumericalValuesAsInteger() throws Exception {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("foo", 1);

    Response response =
        Response.create(200, "OK", Collections.<String, Collection<String>>emptyMap(),
                        "{\"foo\": 1}", UTF_8);
    assertEquals(new GsonDecoder().decode(response, new TypeToken<Map<String, Object>>() {
    }.getType()), map);
  }

  @Test
  public void encodesFormParams() throws Exception {

    Map<String, Object> form = new LinkedHashMap<String, Object>();
    form.put("foo", 1);
    form.put("bar", Arrays.asList(2, 3));

    RequestTemplate template = new RequestTemplate();
    new GsonEncoder().encode(form, new TypeToken<Map<String, ?>>() {
    }.getType(), template);

    assertThat(template).hasBody("" // 
                                 + "{\n" //
                                 + "  \"foo\": 1,\n" //
                                 + "  \"bar\": [\n" //
                                 + "    2,\n" //
                                 + "    3\n" //
                                 + "  ]\n" //
                                 + "}");
  }

  static class Zone extends LinkedHashMap<String, Object> {

    Zone() {
      // for reflective instantiation.
    }

    Zone(String name) {
      this(name, null);
    }

    Zone(String name, String id) {
      put("name", name);
      if (id != null) {
        put("id", id);
      }
    }

    private static final long serialVersionUID = 1L;
  }

  @Test
  public void decodes() throws Exception {

    List<Zone> zones = new LinkedList<Zone>();
    zones.add(new Zone("denominator.io."));
    zones.add(new Zone("denominator.io.", "ABCD"));

    Response response =
        Response.create(200, "OK", Collections.<String, Collection<String>>emptyMap(), zonesJson,
                        UTF_8);
    assertEquals(zones, new GsonDecoder().decode(response, new TypeToken<List<Zone>>() {
    }.getType()));
  }

  @Test
  public void nullBodyDecodesToNull() throws Exception {
    Response response = Response.create(204, "OK",
                                        Collections.<String, Collection<String>>emptyMap(),
                                        (byte[]) null);
    assertNull(new GsonDecoder().decode(response, String.class));
  }

  @Test
  public void emptyBodyDecodesToNull() throws Exception {
    Response response = Response.create(204, "OK",
                                        Collections.<String, Collection<String>>emptyMap(),
                                        new byte[0]);
    assertNull(new GsonDecoder().decode(response, String.class));
  }

  private String zonesJson = ""//
                             + "[\n"//
                             + "  {\n"//
                             + "    \"name\": \"denominator.io.\"\n"//
                             + "  },\n"//
                             + "  {\n"//
                             + "    \"name\": \"denominator.io.\",\n"//
                             + "    \"id\": \"ABCD\"\n"//
                             + "  }\n"//
                             + "]\n";

  final TypeAdapter upperZone = new TypeAdapter<Zone>() {

    @Override
    public void write(JsonWriter out, Zone value) throws IOException {
      out.beginObject();
      for (Map.Entry<String, Object> entry : value.entrySet()) {
        out.name(entry.getKey()).value(entry.getValue().toString().toUpperCase());
      }
      out.endObject();
    }

    @Override
    public Zone read(JsonReader in) throws IOException {
      in.beginObject();
      Zone zone = new Zone();
      while (in.hasNext()) {
        zone.put(in.nextName(), in.nextString().toUpperCase());
      }
      in.endObject();
      return zone;
    }
  };

  @Test
  public void customDecoder() throws Exception {
    GsonDecoder decoder = new GsonDecoder(Arrays.<TypeAdapter<?>>asList(upperZone));

    List<Zone> zones = new LinkedList<Zone>();
    zones.add(new Zone("DENOMINATOR.IO."));
    zones.add(new Zone("DENOMINATOR.IO.", "ABCD"));

    Response response =
        Response.create(200, "OK", Collections.<String, Collection<String>>emptyMap(), zonesJson,
                        UTF_8);
    assertEquals(zones, decoder.decode(response, new TypeToken<List<Zone>>() {
    }.getType()));
  }

  @Test
  public void customEncoder() throws Exception {
    GsonEncoder encoder = new GsonEncoder(Arrays.<TypeAdapter<?>>asList(upperZone));

    List<Zone> zones = new LinkedList<Zone>();
    zones.add(new Zone("denominator.io."));
    zones.add(new Zone("denominator.io.", "abcd"));

    RequestTemplate template = new RequestTemplate();
    encoder.encode(zones, new TypeToken<List<Zone>>() {
    }.getType(), template);

    assertThat(template).hasBody("" //
                                 + "[\n" //
                                 + "  {\n" //
                                 + "    \"name\": \"DENOMINATOR.IO.\"\n" //
                                 + "  },\n" //
                                 + "  {\n" //
                                 + "    \"name\": \"DENOMINATOR.IO.\",\n" //
                                 + "    \"id\": \"ABCD\"\n" //
                                 + "  }\n" //
                                 + "]");
  }

  /** Enabled via {@link feign.Feign.Builder#decode404()} */
  @Test
  public void notFoundDecodesToEmpty() throws Exception {
    Response response = Response.create(404, "NOT FOUND",
        Collections.<String, Collection<String>>emptyMap(),
        (byte[]) null);
    assertThat((byte[]) new GsonDecoder().decode(response, byte[].class)).isEmpty();
  }
}
