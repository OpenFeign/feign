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
package feign.jackson;

import static feign.Util.UTF_8;
import static feign.assertj.FeignAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import feign.Request;
import feign.Request.HttpMethod;
import feign.RequestTemplate;
import feign.Response;
import feign.Util;

@SuppressWarnings("deprecation")
class JacksonCodecTest {

  private String zonesJson = ""//
      + "[" + System.lineSeparator() //
      + "  {" + System.lineSeparator() //
      + "    \"name\": \"denominator.io.\"" + System.lineSeparator()//
      + "  }," + System.lineSeparator()//
      + "  {" + System.lineSeparator()//
      + "    \"name\": \"denominator.io.\"," + System.lineSeparator()//
      + "    \"id\": \"ABCD\"" + System.lineSeparator()//
      + "  }" + System.lineSeparator()//
      + "]" + System.lineSeparator();

  @Test
  void encodesMapObjectNumericalValuesAsInteger() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("foo", 1);

    RequestTemplate template = new RequestTemplate();
    new JacksonEncoder().encode(map, map.getClass(), template);

    assertThat(template).hasBody(""//
        + "{" + System.lineSeparator() //
        + "  \"foo\" : 1" + System.lineSeparator() //
        + "}");
  }

  @Test
  void encodesFormParams() {
    Map<String, Object> form = new LinkedHashMap<>();
    form.put("foo", 1);
    form.put("bar", Arrays.asList(2, 3));

    RequestTemplate template = new RequestTemplate();
    new JacksonEncoder().encode(form, new TypeReference<Map<String, ?>>() {}.getType(), template);

    assertThat(template).hasBody(""//
        + "{" + System.lineSeparator() //
        + "  \"foo\" : 1," + System.lineSeparator() //
        + "  \"bar\" : [ 2, 3 ]" + System.lineSeparator() //
        + "}");
  }

  @Test
  void decodes() throws Exception {
    List<Zone> zones = new LinkedList<>();
    zones.add(new Zone("denominator.io."));
    zones.add(new Zone("denominator.io.", "ABCD"));

    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .body(zonesJson, UTF_8)
        .build();
    assertThat(new JacksonDecoder().decode(response, new TypeReference<List<Zone>>() {}.getType()))
        .isEqualTo(zones);
  }

  @Test
  void nullBodyDecodesToNull() throws Exception {
    Response response = Response.builder()
        .status(204)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .build();
    assertThat(new JacksonDecoder().decode(response, String.class)).isNull();
  }

  @Test
  void emptyBodyDecodesToNull() throws Exception {
    Response response = Response.builder()
        .status(204)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .body(new byte[0])
        .build();
    assertThat(new JacksonDecoder().decode(response, String.class)).isNull();
  }

  @Test
  void customDecoder() throws Exception {
    JacksonDecoder decoder = new JacksonDecoder(
        Arrays.asList(
            new SimpleModule().addDeserializer(Zone.class, new ZoneDeserializer())));

    List<Zone> zones = new LinkedList<>();
    zones.add(new Zone("DENOMINATOR.IO."));
    zones.add(new Zone("DENOMINATOR.IO.", "ABCD"));

    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .body(zonesJson, UTF_8)
        .build();
    assertThat(decoder.decode(response, new TypeReference<List<Zone>>() {}.getType()))
        .isEqualTo(zones);
  }

  @Test
  void customEncoder() {
    JacksonEncoder encoder = new JacksonEncoder(
        Arrays.asList(new SimpleModule().addSerializer(Zone.class, new ZoneSerializer())));

    List<Zone> zones = new LinkedList<>();
    zones.add(new Zone("denominator.io."));
    zones.add(new Zone("denominator.io.", "abcd"));

    RequestTemplate template = new RequestTemplate();
    encoder.encode(zones, new TypeReference<List<Zone>>() {}.getType(), template);

    assertThat(template).hasBody("" //
        + "[ {" + System.lineSeparator()
        + "  \"name\" : \"DENOMINATOR.IO.\"" + System.lineSeparator()
        + "}, {" + System.lineSeparator()
        + "  \"name\" : \"DENOMINATOR.IO.\"," + System.lineSeparator()
        + "  \"id\" : \"ABCD\"" + System.lineSeparator()
        + "} ]");
  }

  @Test
  void decoderCharset() throws IOException {
    Zone zone = new Zone("denominator.io.", "ÁÉÍÓÚÀÈÌÒÙÄËÏÖÜÑ");

    Map<String, Collection<String>> headers = new HashMap<>();
    headers.put("Content-Type", Arrays.asList("application/json;charset=ISO-8859-1"));

    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(headers)
        .body(new String("" //
            + "{" + System.lineSeparator()
            + "  \"name\" : \"DENOMINATOR.IO.\"," + System.lineSeparator()
            + "  \"id\" : \"ÁÉÍÓÚÀÈÌÒÙÄËÏÖÜÑ\"" + System.lineSeparator()
            + "}").getBytes(StandardCharsets.ISO_8859_1))
        .build();
    assertThat(
        ((Zone) new JacksonDecoder().decode(response, new TypeReference<Zone>() {}.getType())))
            .containsEntry("id", zone.get("id"));
  }

  @Test
  void decodesIterator() throws Exception {
    List<Zone> zones = new LinkedList<>();
    zones.add(new Zone("denominator.io."));
    zones.add(new Zone("denominator.io.", "ABCD"));

    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .body(zonesJson, UTF_8)
        .build();
    Object decoded = JacksonIteratorDecoder.create().decode(response,
        new TypeReference<Iterator<Zone>>() {}.getType());
    assertThat(Iterator.class.isAssignableFrom(decoded.getClass())).isTrue();
    assertThat(Closeable.class.isAssignableFrom(decoded.getClass())).isTrue();
    assertThat(asList((Iterator<?>) decoded)).isEqualTo(zones);
  }

  private <T> List<T> asList(Iterator<T> iter) {
    final List<T> copy = new ArrayList<>();
    while (iter.hasNext()) {
      copy.add(iter.next());
    }
    return copy;
  }

  @Test
  void nullBodyDecodesToEmptyIterator() throws Exception {
    Response response = Response.builder()
        .status(204)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .build();
    assertThat((byte[]) JacksonIteratorDecoder.create().decode(response, byte[].class)).isEmpty();
  }

  @Test
  void emptyBodyDecodesToEmptyIterator() throws Exception {
    Response response = Response.builder()
        .status(204)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .body(new byte[0])
        .build();
    assertThat((byte[]) JacksonIteratorDecoder.create().decode(response, byte[].class)).isEmpty();
  }

  static class Zone extends LinkedHashMap<String, Object> {

    private static final long serialVersionUID = 1L;

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
  }

  static class ZoneDeserializer extends StdDeserializer<Zone> {

    public ZoneDeserializer() {
      super(Zone.class);
    }

    @Override
    public Zone deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
      Zone zone = new Zone();
      jp.nextToken();
      while (jp.nextToken() != JsonToken.END_OBJECT) {
        String name = jp.getCurrentName();
        String value = jp.getValueAsString();
        if (value != null) {
          zone.put(name, value.toUpperCase());
        }
      }
      return zone;
    }
  }

  static class ZoneSerializer extends StdSerializer<Zone> {

    public ZoneSerializer() {
      super(Zone.class);
    }

    @Override
    public void serialize(Zone value, JsonGenerator jgen, SerializerProvider provider)
        throws IOException {
      jgen.writeStartObject();
      for (Map.Entry<String, Object> entry : value.entrySet()) {
        jgen.writeFieldName(entry.getKey());
        jgen.writeString(entry.getValue().toString().toUpperCase());
      }
      jgen.writeEndObject();
    }
  }

  /** Enabled via {@link feign.Feign.Builder#dismiss404()} */
  @Test
  void notFoundDecodesToEmpty() throws Exception {
    Response response = Response.builder()
        .status(404)
        .reason("NOT FOUND")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .build();
    assertThat((byte[]) new JacksonDecoder().decode(response, byte[].class)).isEmpty();
  }

  /** Enabled via {@link feign.Feign.Builder#dismiss404()} */
  @Test
  void notFoundDecodesToEmptyIterator() throws Exception {
    Response response = Response.builder()
        .status(404)
        .reason("NOT FOUND")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .build();
    assertThat((byte[]) JacksonIteratorDecoder.create().decode(response, byte[].class)).isEmpty();
  }
}
