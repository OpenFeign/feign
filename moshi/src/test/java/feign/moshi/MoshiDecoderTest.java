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

import static feign.Util.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import feign.Request;
import feign.Response;
import feign.Util;

class MoshiDecoderTest {

  @Test
  void decodes() throws Exception {

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

    assertThat(new MoshiDecoder().decode(response, List.class)).isEqualTo(zones);
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
  void nullBodyDecodesToNull() throws Exception {
    Response response = Response.builder()
        .status(204)
        .reason("OK")
        .headers(Collections.emptyMap())
        .request(Request.create(Request.HttpMethod.GET, "/api", Collections.emptyMap(), null,
            Util.UTF_8))
        .build();
    assertThat(new MoshiDecoder().decode(response, String.class)).isNull();
  }

  @Test
  void emptyBodyDecodesToNull() throws Exception {
    Response response = Response.builder()
        .status(204)
        .reason("OK")
        .headers(Collections.emptyMap())
        .request(Request.create(Request.HttpMethod.GET, "/api", Collections.emptyMap(), null,
            Util.UTF_8))
        .body(new byte[0])
        .build();
    assertThat(new MoshiDecoder().decode(response, String.class)).isNull();
  }


  /** Enabled via {@link feign.Feign.Builder#dismiss404()} */
  @Test
  void notFoundDecodesToEmpty() throws Exception {
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
  void customDecoder() throws Exception {
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

    assertThat(decoder.decode(response, UpperZoneJSONAdapter.class)).isEqualTo(zones);
  }

  @Test
  void customObjectDecoder() throws Exception {
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
