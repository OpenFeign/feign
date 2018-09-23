/**
 * Copyright 2012-2018 The Feign Authors
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
package feign.gson;

import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import feign.Request;
import feign.Request.HttpMethod;
import feign.Util;
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
  public void encodesMapObjectNumericalValuesAsInteger() {
    Map<String, Object> map = new LinkedHashMap<>();
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
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("foo", 1);

    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .body("{\"foo\": 1}", UTF_8)
        .build();
    assertEquals(
        new GsonDecoder().decode(response, new TypeToken<Map<String, Object>>() {}.getType()), map);
  }

  @Test
  public void encodesFormParams() {

    Map<String, Object> form = new LinkedHashMap<>();
    form.put("foo", 1);
    form.put("bar", Arrays.asList(2, 3));

    RequestTemplate template = new RequestTemplate();
    new GsonEncoder().encode(form, new TypeToken<Map<String, ?>>() {}.getType(), template);

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

    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .headers(Collections.emptyMap())
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .body(zonesJson, UTF_8)
        .build();
    assertEquals(zones,
        new GsonDecoder().decode(response, new TypeToken<List<Zone>>() {}.getType()));
  }

  @Test
  public void nullBodyDecodesToNull() throws Exception {
    Response response = Response.builder()
        .status(204)
        .reason("OK")
        .headers(Collections.emptyMap())
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .build();
    assertNull(new GsonDecoder().decode(response, String.class));
  }

  @Test
  public void emptyBodyDecodesToNull() throws Exception {
    Response response = Response.builder()
        .status(204)
        .reason("OK")
        .headers(Collections.emptyMap())
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .body(new byte[0])
        .build();
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
    GsonDecoder decoder = new GsonDecoder(Collections.singletonList(upperZone));

    List<Zone> zones = new LinkedList<>();
    zones.add(new Zone("DENOMINATOR.IO."));
    zones.add(new Zone("DENOMINATOR.IO.", "ABCD"));

    Response response =
        Response.builder()
            .status(200)
            .reason("OK")
            .headers(Collections.emptyMap())
            .request(
                Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
            .body(zonesJson, UTF_8)
            .build();
    assertEquals(zones, decoder.decode(response, new TypeToken<List<Zone>>() {}.getType()));
  }

  @Test
  public void customEncoder() {
    GsonEncoder encoder = new GsonEncoder(Collections.singletonList(upperZone));

    List<Zone> zones = new LinkedList<>();
    zones.add(new Zone("denominator.io."));
    zones.add(new Zone("denominator.io.", "abcd"));

    RequestTemplate template = new RequestTemplate();
    encoder.encode(zones, new TypeToken<List<Zone>>() {}.getType(), template);

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
    Response response = Response.builder()
        .status(404)
        .reason("NOT FOUND")
        .headers(Collections.emptyMap())
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .build();
    assertThat((byte[]) new GsonDecoder().decode(response, byte[].class)).isEmpty();
  }
}
