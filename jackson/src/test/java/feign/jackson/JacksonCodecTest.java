package feign.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

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

public class JacksonCodecTest {

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

  @Test
  public void encodesMapObjectNumericalValuesAsInteger() throws Exception {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("foo", 1);

    RequestTemplate template = new RequestTemplate();
    new JacksonEncoder().encode(map, map.getClass(), template);

    assertThat(template).hasBody(""//
                                 + "{\n" //
                                 + "  \"foo\" : 1\n" //
                                 + "}");
  }

  @Test
  public void encodesFormParams() throws Exception {
    Map<String, Object> form = new LinkedHashMap<String, Object>();
    form.put("foo", 1);
    form.put("bar", Arrays.asList(2, 3));

    RequestTemplate template = new RequestTemplate();
    new JacksonEncoder().encode(form, new TypeReference<Map<String, ?>>() {
    }.getType(), template);

    assertThat(template).hasBody(""//
                                 + "{\n" //
                                 + "  \"foo\" : 1,\n" //
                                 + "  \"bar\" : [ 2, 3 ]\n" //
                                 + "}");
  }

  @Test
  public void decodes() throws Exception {
    List<Zone> zones = new LinkedList<Zone>();
    zones.add(new Zone("denominator.io."));
    zones.add(new Zone("denominator.io.", "ABCD"));

    Response response =
        Response.create(200, "OK", Collections.<String, Collection<String>>emptyMap(), zonesJson,
                        UTF_8);
    assertEquals(zones, new JacksonDecoder().decode(response, new TypeReference<List<Zone>>() {
    }.getType()));
  }

  @Test
  public void nullBodyDecodesToNull() throws Exception {
    Response
        response =
        Response
            .create(204, "OK", Collections.<String, Collection<String>>emptyMap(), (byte[]) null);
    assertNull(new JacksonDecoder().decode(response, String.class));
  }

  @Test
  public void emptyBodyDecodesToNull() throws Exception {
    Response response = Response.create(204, "OK",
                                        Collections.<String, Collection<String>>emptyMap(),
                                        new byte[0]);
    assertNull(new JacksonDecoder().decode(response, String.class));
  }

  @Test
  public void customDecoder() throws Exception {
    JacksonDecoder decoder = new JacksonDecoder(
        Arrays.<Module>asList(
            new SimpleModule().addDeserializer(Zone.class, new ZoneDeserializer())));

    List<Zone> zones = new LinkedList<Zone>();
    zones.add(new Zone("DENOMINATOR.IO."));
    zones.add(new Zone("DENOMINATOR.IO.", "ABCD"));

    Response response =
        Response.create(200, "OK", Collections.<String, Collection<String>>emptyMap(), zonesJson,
                        UTF_8);
    assertEquals(zones, decoder.decode(response, new TypeReference<List<Zone>>() {
    }.getType()));
  }

  @Test
  public void customEncoder() throws Exception {
    JacksonEncoder encoder = new JacksonEncoder(
        Arrays.<Module>asList(new SimpleModule().addSerializer(Zone.class, new ZoneSerializer())));

    List<Zone> zones = new LinkedList<Zone>();
    zones.add(new Zone("denominator.io."));
    zones.add(new Zone("denominator.io.", "abcd"));

    RequestTemplate template = new RequestTemplate();
    encoder.encode(zones, new TypeReference<List<Zone>>() {
    }.getType(), template);

    assertThat(template).hasBody("" //
                                 + "[ {\n"
                                 + "  \"name\" : \"DENOMINATOR.IO.\"\n"
                                 + "}, {\n"
                                 + "  \"name\" : \"DENOMINATOR.IO.\",\n"
                                 + "  \"id\" : \"ABCD\"\n"
                                 + "} ]");
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

  /** Enabled via {@link feign.Feign.Builder#decode404()} */
  @Test
  public void notFoundDecodesToEmpty() throws Exception {
    Response response = Response.create(404, "NOT FOUND",
        Collections.<String, Collection<String>>emptyMap(),
        (byte[]) null);
    assertThat((byte[]) new JacksonDecoder().decode(response, byte[].class)).isEmpty();
  }
}
