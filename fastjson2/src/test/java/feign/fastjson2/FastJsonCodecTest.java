/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
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
package feign.fastjson2;

import static feign.Util.UTF_8;
import static feign.assertj.FeignAssertions.assertThat;

import com.alibaba.fastjson2.TypeReference;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import feign.Util;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * @author changjin wei(魏昌进)
 */
@SuppressWarnings("deprecation")
class FastJsonCodecTest {

  private String zonesJson =
      "" //
          + "["
          + System.lineSeparator() //
          + "  {"
          + System.lineSeparator() //
          + "    \"name\": \"denominator.io.\""
          + System.lineSeparator() //
          + "  },"
          + System.lineSeparator() //
          + "  {"
          + System.lineSeparator() //
          + "    \"name\": \"denominator.io.\","
          + System.lineSeparator() //
          + "    \"id\": \"ABCD\""
          + System.lineSeparator() //
          + "  }"
          + System.lineSeparator() //
          + "]"
          + System.lineSeparator();

  @Test
  void encodesMapObjectNumericalValuesAsInteger() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("foo", 1);

    RequestTemplate template = new RequestTemplate();
    new Fastjson2Encoder().encode(map, map.getClass(), template);

    assertThat(template)
        .hasBody(
            "" //
                + "{" //
                + "\"foo\":1" //
                + "}");
  }

  @Test
  void encodesFormParams() {
    Map<String, Object> form = new LinkedHashMap<>();
    form.put("foo", 1);
    form.put("bar", Arrays.asList(2, 3));

    RequestTemplate template = new RequestTemplate();
    new Fastjson2Encoder().encode(form, new TypeReference<Map<String, ?>>() {}.getType(), template);

    assertThat(template)
        .hasBody(
            "" //
                + "{" //
                + "\"foo\":1," //
                + "\"bar\":[2,3]" //
                + "}");
  }

  @Test
  void decodes() throws Exception {
    List<Zone> zones = new LinkedList<>();
    zones.add(new Zone("denominator.io."));
    zones.add(new Zone("denominator.io.", "ABCD"));

    Response response =
        Response.builder()
            .status(200)
            .reason("OK")
            .request(
                Request.create(
                    Request.HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
            .headers(Collections.emptyMap())
            .body(zonesJson, UTF_8)
            .build();
    assertThat(
            new Fastjson2Decoder().decode(response, new TypeReference<List<Zone>>() {}.getType()))
        .isEqualTo(zones);
  }

  @Test
  void nullBodyDecodesToNull() throws Exception {
    Response response =
        Response.builder()
            .status(204)
            .reason("OK")
            .request(
                Request.create(
                    Request.HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
            .headers(Collections.emptyMap())
            .build();
    assertThat(new Fastjson2Decoder().decode(response, String.class)).isNull();
  }

  @Test
  void emptyBodyDecodesToNull() throws Exception {
    Response response =
        Response.builder()
            .status(204)
            .reason("OK")
            .request(
                Request.create(
                    Request.HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
            .headers(Collections.emptyMap())
            .body(new byte[0])
            .build();
    assertThat(new Fastjson2Decoder().decode(response, String.class)).isNull();
  }

  @Test
  void decoderCharset() throws IOException {
    Zone zone = new Zone("denominator.io.", "ÁÉÍÓÚÀÈÌÒÙÄËÏÖÜÑ");

    Map<String, Collection<String>> headers = new HashMap<>();
    headers.put("Content-Type", Arrays.asList("application/json;charset=ISO-8859-1"));

    Response response =
        Response.builder()
            .status(200)
            .reason("OK")
            .request(
                Request.create(
                    Request.HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
            .headers(headers)
            .body(
                new String(
                        """
                        {\
                          "name" : "DENOMINATOR.IO.",\
                          "id" : "ÁÉÍÓÚÀÈÌÒÙÄËÏÖÜÑ"\
                        }\
                        """)
                    .getBytes(StandardCharsets.ISO_8859_1))
            .build();
    assertThat(
            ((Zone)
                new Fastjson2Decoder().decode(response, new TypeReference<Zone>() {}.getType())))
        .containsEntry("id", zone.get("id"));
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

  /** Enabled via {@link feign.Feign.Builder#dismiss404()} */
  @Test
  void notFoundDecodesToEmpty() throws Exception {
    Response response =
        Response.builder()
            .status(404)
            .reason("NOT FOUND")
            .request(
                Request.create(
                    Request.HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
            .headers(Collections.emptyMap())
            .build();
    assertThat((byte[]) new Fastjson2Decoder().decode(response, byte[].class)).isEmpty();
  }
}
