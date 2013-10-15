package feign.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.reflect.TypeToken;
import dagger.Module;
import dagger.ObjectGraph;
import dagger.Provides;
import feign.RequestTemplate;
import feign.Response;
import feign.codec.Decoder;
import feign.codec.Encoder;
import org.testng.annotations.Test;

import javax.inject.Inject;
import java.io.IOException;
import java.util.*;

import static org.testng.Assert.assertEquals;

@Test
public class JacksonModuleTest {
  @Module(includes = JacksonModule.class, injects = EncoderAndDecoderBindings.class)
  static class EncoderAndDecoderBindings {
    @Inject
    Encoder encoder;
    @Inject
    Decoder decoder;
  }

  @Test
  public void providesEncoderDecoder() throws Exception {
    EncoderAndDecoderBindings bindings = new EncoderAndDecoderBindings();
    ObjectGraph.create(bindings).inject(bindings);

    assertEquals(bindings.encoder.getClass(), JacksonEncoder.class);
    assertEquals(bindings.decoder.getClass(), JacksonDecoder.class);
  }

  @Module(includes = JacksonModule.class, injects = EncoderBindings.class)
  static class EncoderBindings {
    @Inject Encoder encoder;
  }

  @Test public void encodesMapObjectNumericalValuesAsInteger() throws Exception {
    EncoderBindings bindings = new EncoderBindings();
    ObjectGraph.create(bindings).inject(bindings);

    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("foo", 1);

    RequestTemplate template = new RequestTemplate();
    bindings.encoder.encode(map, template);
    assertEquals(template.body(), ""//
        + "{\n" //
        + "  \"foo\" : 1\n" //
        + "}");
  }

  @Test public void encodesFormParams() throws Exception {
    EncoderBindings bindings = new EncoderBindings();
    ObjectGraph.create(bindings).inject(bindings);

    Map<String, Object> form = new LinkedHashMap<String, Object>();
    form.put("foo", 1);
    form.put("bar", Arrays.asList(2, 3));

    RequestTemplate template = new RequestTemplate();
    bindings.encoder.encode(form, template);
    assertEquals(template.body(), ""//
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

  @Module(includes = JacksonModule.class, injects = DecoderBindings.class)
  static class DecoderBindings {
    @Inject Decoder decoder;
  }

  @Test public void decodes() throws Exception {
    DecoderBindings bindings = new DecoderBindings();
    ObjectGraph.create(bindings).inject(bindings);

    List<Zone> zones = new LinkedList<Zone>();
    zones.add(new Zone("denominator.io."));
    zones.add(new Zone("denominator.io.", "ABCD"));

    Response response = Response.create(200, "OK", Collections.<String, Collection<String>>emptyMap(), zonesJson);
    assertEquals(bindings.decoder.decode(response, new TypeToken<List<Zone>>() {
    }.getType()), zones);
  }

  @Test public void nullBodyDecodesToNull() throws Exception {
    DecoderBindings bindings = new DecoderBindings();
    ObjectGraph.create(bindings).inject(bindings);

    Response response = Response.create(204, "OK", Collections.<String, Collection<String>>emptyMap(), null);
    assertEquals(bindings.decoder.decode(response, String.class), null);
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

  @Module(includes = JacksonModule.class, injects = CustomJacksonModule.class)
  static class CustomJacksonModule {
    @Inject Decoder decoder;

    @Provides(type = Provides.Type.SET)
    com.fasterxml.jackson.databind.Module upperZone() {
      return new ZoneModule();
    }
  }

  @Test public void customDecoder() throws Exception {
    CustomJacksonModule bindings = new CustomJacksonModule();
    ObjectGraph.create(bindings).inject(bindings);

    List<Zone> zones = new LinkedList<Zone>();
    zones.add(new Zone("DENOMINATOR.IO."));
    zones.add(new Zone("DENOMINATOR.IO.", "ABCD"));

    Response response = Response.create(200, "OK", Collections.<String, Collection<String>>emptyMap(), zonesJson);
    assertEquals(bindings.decoder.decode(response, new TypeToken<List<Zone>>() {
    }.getType()), zones);
  }
}
