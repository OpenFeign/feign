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

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import feign.Request;
import feign.Response;
import feign.Util;
import org.junit.Test;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import static feign.Util.UTF_8;
import static feign.assertj.FeignAssertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MoshiDecoderTest {

  @Test
  public void decodes() throws Exception {

    class Zone extends LinkedHashMap<String, Object> {

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

    List<Zone> zones = new LinkedList<>();
    zones.add(new Zone("denominator.io."));
    zones.add(new Zone("denominator.io.", "ABCD"));

    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .headers(Collections.emptyMap())
        .request(Request.create(Request.HttpMethod.GET, "/api", Collections.emptyMap(), null,
            Util.UTF_8))
        .body(zonesJson, UTF_8)
        .build();

    assertEquals(zones,
        new MoshiDecoder().decode(response, List.class));
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

  private final String videoGamesJson = "{\n   " +
      " \"hero\": {\n     " +
      " \"enemy\": \"Bowser\",\n     " +
      " \"name\": \"Luigi\"\n    " +
      "},\n    " +
      "\"name\": \"Super Mario\"\n  " +
      "}";

  @Test
  public void nullBodyDecodesToNull() throws Exception {
    Response response = Response.builder()
        .status(204)
        .reason("OK")
        .headers(Collections.emptyMap())
        .request(Request.create(Request.HttpMethod.GET, "/api", Collections.emptyMap(), null,
            Util.UTF_8))
        .build();
    assertNull(new MoshiDecoder().decode(response, String.class));
  }

  @Test
  public void emptyBodyDecodesToNull() throws Exception {
    Response response = Response.builder()
        .status(204)
        .reason("OK")
        .headers(Collections.emptyMap())
        .request(Request.create(Request.HttpMethod.GET, "/api", Collections.emptyMap(), null,
            Util.UTF_8))
        .body(new byte[0])
        .build();
    assertNull(new MoshiDecoder().decode(response, String.class));
  }


  /** Enabled via {@link feign.Feign.Builder#dismiss404()} */
  @Test
  public void notFoundDecodesToEmpty() throws Exception {
    Response response = Response.builder()
        .status(404)
        .reason("NOT FOUND")
        .headers(Collections.emptyMap())
        .request(Request.create(Request.HttpMethod.GET, "/api", Collections.emptyMap(), null,
            Util.UTF_8))
        .build();
    assertThat((byte[]) new MoshiDecoder().decode(response, byte[].class)).isEmpty();
  }

  @Test
  public void customDecoder() throws Exception {
    final UpperZoneJSONAdapter upperZoneAdapter = new UpperZoneJSONAdapter();

    MoshiDecoder decoder = new MoshiDecoder(Collections.singleton(upperZoneAdapter));

    List<Zone> zones = new LinkedList<>();
    zones.add(new Zone("DENOMINATOR.IO."));
    zones.add(new Zone("DENOMINATOR.IO.", "ABCD"));

    Response response =
        Response.builder()
            .status(200)
            .reason("OK")
            .headers(Collections.emptyMap())
            .request(
                Request.create(Request.HttpMethod.GET, "/api", Collections.emptyMap(), null,
                    Util.UTF_8))
            .body(zonesJson, UTF_8)
            .build();

    assertEquals(zones, decoder.decode(response, UpperZoneJSONAdapter.class));
  }

  @Test
  public void customObjectDecoder() throws Exception {
    final JsonAdapter<VideoGame> videoGameJsonAdapter =
        new Moshi.Builder().build().adapter(VideoGame.class);

    MoshiDecoder decoder = new MoshiDecoder(Collections.singleton(videoGameJsonAdapter));

    VideoGame videoGame = new VideoGame("Super Mario", "Luigi", "Bowser");

    Response response =
        Response.builder()
            .status(200)
            .reason("OK")
            .headers(Collections.emptyMap())
            .request(
                Request.create(Request.HttpMethod.GET, "/api", Collections.emptyMap(), null,
                    Util.UTF_8))
            .body(videoGamesJson, UTF_8)
            .build();

    VideoGame actual = (VideoGame) decoder.decode(response, videoGameJsonAdapter.getClass());

    assertThat(actual)
        .isEqualToComparingFieldByFieldRecursively(videoGame);
  }
}
