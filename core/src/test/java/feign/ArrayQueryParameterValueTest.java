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
package feign;

import static org.assertj.core.api.Assertions.assertThat;

import feign.querymap.FieldQueryMapEncoder;
import feign.template.UriUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for array valued query parameters.
 *
 * <p>Collections ({@link Iterable}, {@code Object[]}) are expanded into repeated query parameters.
 * Arrays of primitives are arrays too and must be expanded the same way, instead of reaching the
 * url as the array's identity string, e.g. {@code ids=%5BI%401b6d3586}.
 */
class ArrayQueryParameterValueTest {

  interface ArrayApi {

    @RequestLine("GET /x")
    Response queryMap(@QueryMap Map<String, Object> parameters);

    @RequestLine("GET /x")
    Response queryMapDto(@QueryMap IntArrayDto dto);

    @RequestLine("GET /x?ids={ids}")
    Response idsParameter(@Param("ids") int[] ids);

    @RequestLine("GET /x?ids={ids}")
    Response idsListParameter(@Param("ids") List<String> ids);

    @RequestLine("GET /x?names={names}")
    Response namesParameter(@Param("names") String[] names);

    @RequestLine("GET /x?ids={ids}")
    Response expandedIdsParameter(
        @Param(value = "ids", expander = WholeArrayExpander.class) int[] ids);

    @RequestLine("GET /x{;ids}")
    Response pathStyleParameter(@Param("ids") int[] ids);
  }

  static class IntArrayDto {
    int[] ids = new int[] {1, 2, 3};
  }

  /** Expander that receives the parameter value as a whole and remembers what it received. */
  static class WholeArrayExpander implements Param.Expander {

    static Object captured;

    @Override
    public String expand(Object value) {
      captured = value;
      return "whole";
    }
  }

  /** Captures the url Feign would put on the wire, without any network or HTTP client. */
  static class RecordingClient implements Client {

    String url;

    @Override
    public Response execute(Request request, Request.Options options) throws IOException {
      this.url = request.url();
      return Response.builder()
          .status(200)
          .reason("OK")
          .headers(Collections.emptyMap())
          .request(request)
          .body(new byte[0])
          .build();
    }
  }

  private static <T> T api(RecordingClient client, Class<T> type) {
    return Feign.builder()
        .client(client)
        .queryMapEncoder(new FieldQueryMapEncoder())
        .target(type, "http://localhost");
  }

  @Test
  void primitiveArrayQueryMapValueIsExpanded() {
    RecordingClient client = new RecordingClient();
    Map<String, Object> parameters = new LinkedHashMap<>();
    parameters.put("ids", new int[] {1, 2});

    api(client, ArrayApi.class).queryMap(parameters);

    assertThat(client.url).endsWith("/x?ids=1&ids=2");
  }

  @Test
  void primitiveArrayQueryMapFieldIsExpanded() {
    RecordingClient client = new RecordingClient();

    api(client, ArrayApi.class).queryMapDto(new IntArrayDto());

    assertThat(client.url).endsWith("/x?ids=1&ids=2&ids=3");
  }

  @Test
  void primitiveArrayParameterIsExpanded() {
    RecordingClient client = new RecordingClient();

    api(client, ArrayApi.class).idsParameter(new int[] {1, 2});

    assertThat(client.url).endsWith("/x?ids=1&ids=2");
  }

  @Test
  void objectArrayParameterIsExpanded() {
    RecordingClient client = new RecordingClient();

    api(client, ArrayApi.class).namesParameter(new String[] {"a", "b"});

    assertThat(client.url).endsWith("/x?names=a&names=b");
  }

  @Test
  void arrayElementsAreEncodedIndividually() {
    RecordingClient client = new RecordingClient();

    api(client, ArrayApi.class).namesParameter(new String[] {"a,b", "c d"});

    assertThat(valuesOf(client.url, "names")).containsExactly("a,b", "c d");
  }

  @Test
  void nullArrayParameterIsSkipped() {
    RecordingClient client = new RecordingClient();

    api(client, ArrayApi.class).idsParameter(null);

    assertThat(client.url).isEqualTo("http://localhost/x");
  }

  @Test
  void emptyPrimitiveArrayParameterMatchesEmptyList() {
    RecordingClient emptyArray = new RecordingClient();
    api(emptyArray, ArrayApi.class).idsParameter(new int[0]);

    RecordingClient emptyList = new RecordingClient();
    api(emptyList, ArrayApi.class).idsListParameter(Collections.emptyList());

    assertThat(emptyArray.url).isEqualTo(emptyList.url);
  }

  @Test
  void emptyPrimitiveArrayQueryMapValueAddsNoParameter() {
    RecordingClient client = new RecordingClient();
    Map<String, Object> parameters = new LinkedHashMap<>();
    parameters.put("ids", new int[0]);

    api(client, ArrayApi.class).queryMap(parameters);

    assertThat(client.url).isEqualTo("http://localhost/x");
  }

  @Test
  void nullPrimitiveArrayQueryMapValueAddsNoParameter() {
    RecordingClient client = new RecordingClient();
    Map<String, Object> parameters = new LinkedHashMap<>();
    parameters.put("ids", null);

    api(client, ArrayApi.class).queryMap(parameters);

    assertThat(client.url).isEqualTo("http://localhost/x");
  }

  /**
   * Control: an explicit {@link Param.Expander} receives the parameter value as a whole, so custom
   * expanders that handle arrays themselves keep working.
   */
  @Test
  void customExpanderReceivesArrayAsWholeValue() {
    WholeArrayExpander.captured = null;
    RecordingClient client = new RecordingClient();

    api(client, ArrayApi.class).expandedIdsParameter(new int[] {1, 2});

    assertThat(WholeArrayExpander.captured).isInstanceOf(int[].class);
    assertThat((int[]) WholeArrayExpander.captured).containsExactly(1, 2);
    assertThat(client.url).isEqualTo("http://localhost/x?ids=whole");
  }

  @Test
  void pathStyleParameterExpandsArrayElements() {
    RecordingClient client = new RecordingClient();

    api(client, ArrayApi.class).pathStyleParameter(new int[] {1, 2});

    assertThat(client.url).isEqualTo("http://localhost/x;ids=1;ids=2");
  }

  /** Control: {@code Object[]} query map values are already expanded. */
  @Test
  void objectArrayQueryMapValueIsExpanded() {
    RecordingClient client = new RecordingClient();
    Map<String, Object> parameters = new LinkedHashMap<>();
    parameters.put("names", new String[] {"a", "b"});

    api(client, ArrayApi.class).queryMap(parameters);

    assertThat(client.url).endsWith("/x?names=a&names=b");
  }

  /** Decoded values of the named query parameter, so that {@code ,} and {@code %2C} are equal. */
  private static List<String> valuesOf(String url, String name) {
    List<String> values = new ArrayList<>();
    int queryStart = url.indexOf('?');
    if (queryStart < 0) {
      return values;
    }
    for (String parameter : url.substring(queryStart + 1).split("&")) {
      int separator = parameter.indexOf('=');
      String rawName = separator < 0 ? parameter : parameter.substring(0, separator);
      String parameterName = UriUtils.decode(rawName, Util.UTF_8);
      if (name.equals(parameterName)) {
        values.add(
            separator < 0 ? null : UriUtils.decode(parameter.substring(separator + 1), Util.UTF_8));
      }
    }
    return values;
  }
}
