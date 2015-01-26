package feign.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import feign.RequestTemplate;
import feign.Response;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static feign.Util.UTF_8;
import static feign.assertj.FeignAssertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class JacksonCodecTest {

  @Test public void encodesMapObjectNumericalValuesAsInteger() throws Exception {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("foo", 1);

    RequestTemplate template = new RequestTemplate();
    new JacksonEncoder().encode(map, template);

    assertThat(template).hasBody(""//
        + "{\n" //
        + "  \"foo\" : 1\n" //
        + "}");
  }

  @Test public void encodesFormParams() throws Exception {
    Map<String, Object> form = new LinkedHashMap<String, Object>();
    form.put("foo", 1);
    form.put("bar", Arrays.asList(2, 3));

    RequestTemplate template = new RequestTemplate();
    new JacksonEncoder().encode(form, template);

    assertThat(template).hasBody(""//
        + "{\n" //
        + "  \"foo\" : 1,\n" //
        + "  \"bar\" : [ 2, 3 ]\n" //
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

  @Test public void decodes() throws Exception {
    List<Zone> zones = new LinkedList<Zone>();
    zones.add(new Zone("denominator.io."));
    zones.add(new Zone("denominator.io.", "ABCD"));

    Response response =
        Response.create(200, "OK", Collections.<String, Collection<String>>emptyMap(), zonesJson, UTF_8);
    assertEquals(zones, new JacksonDecoder().decode(response, new TypeReference<List<Zone>>() {
    }.getType()));
  }

  @Test public void nullBodyDecodesToNull() throws Exception {
    Response response = Response.create(204, "OK", Collections.<String, Collection<String>>emptyMap(), (byte[]) null);
    assertNull(new JacksonDecoder().decode(response, String.class));
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

  static class ZoneModule extends SimpleModule {
    public ZoneModule() {
      addDeserializer(Zone.class, new ZoneDeserializer());
    }
  }

  @Test public void customDecoder() throws Exception {
    JacksonDecoder decoder = new JacksonDecoder(Arrays.<Module>asList(new ZoneModule()));

    List<Zone> zones = new LinkedList<Zone>();
    zones.add(new Zone("DENOMINATOR.IO."));
    zones.add(new Zone("DENOMINATOR.IO.", "ABCD"));

    Response response =
        Response.create(200, "OK", Collections.<String, Collection<String>>emptyMap(), zonesJson, UTF_8);
    assertEquals(zones, decoder.decode(response, new TypeReference<List<Zone>>() {
    }.getType()));
  }
}
