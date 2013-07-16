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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import com.google.gson.reflect.TypeToken;
import dagger.Module;
import dagger.ObjectGraph;
import feign.IncrementalCallback;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.IncrementalDecoder;
import java.io.StringReader;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import org.testng.annotations.Test;

@Test
public class GsonModuleTest {

  @Test
  public void providesEncoderDecoderAndIncrementalDecoder() throws Exception {
    @Module(includes = GsonModule.class, injects = SetBindings.class)
    class SetBindings {
      @Inject Set<Encoder> encoders;
      @Inject Set<Decoder> decoders;
      @Inject Set<IncrementalDecoder> incrementalDecoders;
    }

    SetBindings bindings = new SetBindings();
    ObjectGraph.create(bindings).inject(bindings);

    assertEquals(bindings.encoders.size(), 1);
    assertEquals(bindings.encoders.iterator().next().getClass(), GsonModule.GsonCodec.class);
    assertEquals(bindings.decoders.size(), 1);
    assertEquals(bindings.decoders.iterator().next().getClass(), GsonModule.GsonCodec.class);
    assertEquals(bindings.incrementalDecoders.size(), 1);
    assertEquals(
        bindings.incrementalDecoders.iterator().next().getClass(), GsonModule.GsonCodec.class);
  }

  @Test
  public void encodesMapObjectNumericalValuesAsInteger() throws Exception {
    @Module(includes = GsonModule.class, injects = SetBindings.class)
    class SetBindings {
      @Inject Set<Encoder> encoders;
    }

    SetBindings bindings = new SetBindings();
    ObjectGraph.create(bindings).inject(bindings);

    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("foo", 1);

    assertEquals(
        Encoder.Text.class.cast(bindings.encoders.iterator().next()).encode(map),
        "" //
            + "{\n" //
            + "  \"foo\": 1\n" //
            + "}");
  }

  @Test
  public void encodesFormParams() throws Exception {
    @Module(includes = GsonModule.class, injects = SetBindings.class)
    class SetBindings {
      @Inject Set<Encoder> encoders;
    }

    SetBindings bindings = new SetBindings();
    ObjectGraph.create(bindings).inject(bindings);

    Map<String, Object> form = new LinkedHashMap<String, Object>();
    form.put("foo", 1);
    form.put("bar", Arrays.asList(2, 3));

    assertEquals(
        Encoder.Text.class.cast(bindings.encoders.iterator().next()).encode(form),
        "" //
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
      if (id != null) put("id", id);
    }

    private static final long serialVersionUID = 1L;
  }

  @Test
  public void decodes() throws Exception {
    @Module(includes = GsonModule.class, injects = SetBindings.class)
    class SetBindings {
      @Inject Set<Decoder> decoders;
    }

    SetBindings bindings = new SetBindings();
    ObjectGraph.create(bindings).inject(bindings);

    List<Zone> zones = new LinkedList<Zone>();
    zones.add(new Zone("denominator.io."));
    zones.add(new Zone("denominator.io.", "ABCD"));

    assertEquals(
        Decoder.TextStream.class
            .cast(bindings.decoders.iterator().next())
            .decode(new StringReader(zonesJson), new TypeToken<List<Zone>>() {}.getType()),
        zones);
  }

  @Test
  public void decodesIncrementally() throws Exception {
    @Module(includes = GsonModule.class, injects = SetBindings.class)
    class SetBindings {
      @Inject Set<IncrementalDecoder> decoders;
    }

    SetBindings bindings = new SetBindings();
    ObjectGraph.create(bindings).inject(bindings);

    final List<Zone> zones = new LinkedList<Zone>();
    zones.add(new Zone("denominator.io."));
    zones.add(new Zone("denominator.io.", "ABCD"));

    final AtomicInteger index = new AtomicInteger(0);

    IncrementalCallback<Zone> zoneCallback =
        new IncrementalCallback<Zone>() {

          @Override
          public void onNext(Zone element) {
            assertEquals(element, zones.get(index.getAndIncrement()));
          }

          @Override
          public void onSuccess() {
            // decoder shouldn't call onSuccess
            fail();
          }

          @Override
          public void onFailure(Throwable cause) {
            // decoder shouldn't call onFailure
            fail();
          }
        };

    IncrementalDecoder.TextStream.class
        .cast(bindings.decoders.iterator().next())
        .decode(new StringReader(zonesJson), Zone.class, zoneCallback);

    assertEquals(index.get(), 2);
  }

  private String zonesJson =
      "" //
          + "[\n" //
          + "  {\n" //
          + "    \"name\": \"denominator.io.\"\n" //
          + "  },\n" //
          + "  {\n" //
          + "    \"name\": \"denominator.io.\",\n" //
          + "    \"id\": \"ABCD\"\n" //
          + "  }\n" //
          + "]\n";
}
