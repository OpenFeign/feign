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

import static feign.assertj.FeignAssertions.assertThat;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import feign.RequestTemplate;

class MoshiEncoderTest {

  @Test
  void encodesMapObjectNumericalValuesAsInteger() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("foo", 1);

    RequestTemplate template = new RequestTemplate();
    new MoshiEncoder().encode(map, Map.class, template);

    assertThat(template).hasBody("{\n" //
        + "  \"foo\": 1\n" //
        + "}");
  }

  @Test
  void encodesFormParams() {

    Map<String, Object> form = new LinkedHashMap<>();
    form.put("foo", 1);
    form.put("bar", Arrays.asList(2, 3));

    RequestTemplate template = new RequestTemplate();

    new MoshiEncoder().encode(form, Map.class, template);

    assertThat(template).hasBody("{\n" //
        + "  \"foo\": 1,\n" //
        + "  \"bar\": [\n" //
        + "    2,\n" //
        + "    3\n" //
        + "  ]\n" //
        + "}");
  }

  @Test
  void customEncoder() {
    final UpperZoneJSONAdapter upperZoneAdapter = new UpperZoneJSONAdapter();

    MoshiEncoder encoder = new MoshiEncoder(Collections.singleton(upperZoneAdapter));

    List<Zone> zones = new LinkedList<>();
    zones.add(new Zone("denominator.io."));
    zones.add(new Zone("denominator.io.", "abcd"));

    RequestTemplate template = new RequestTemplate();
    encoder.encode(zones, UpperZoneJSONAdapter.class, template);

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

  @Test
  void customObjectEncoder() {
    final JsonAdapter<VideoGame> videoGameJsonAdapter =
        new Moshi.Builder().build().adapter(VideoGame.class);
    MoshiEncoder encoder = new MoshiEncoder(Collections.singleton(videoGameJsonAdapter));

    VideoGame videoGame = new VideoGame("Super Mario", "Luigi", "Bowser");

    RequestTemplate template = new RequestTemplate();
    encoder.encode(videoGame, videoGameJsonAdapter.getClass(), template);


    assertThat(template)
        .hasBody("{\n" +
            "  \"hero\": {\n" +
            "    \"enemy\": \"Bowser\",\n" +
            "    \"name\": \"Luigi\"\n" +
            "  },\n" +
            "  \"name\": \"Super Mario\"\n" +
            "}");
  }
}

