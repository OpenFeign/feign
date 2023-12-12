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
package feign.json;

import static feign.Util.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Collections;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import feign.Request;
import feign.Response;
import feign.codec.DecodeException;

class JsonDecoderTest {

  private static JSONArray jsonArray;
  private static JSONObject jsonObject;
  private static Request request;

  @BeforeAll
  static void setUpClass() {
    jsonObject = new JSONObject();
    jsonObject.put("a", "b");
    jsonObject.put("c", 1);
    jsonArray = new JSONArray();
    jsonArray.put(jsonObject);
    jsonArray.put(123);
    request = Request.create(Request.HttpMethod.GET, "/qwerty", Collections.emptyMap(),
        "xyz".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8, null);
  }

  @Test
  void decodesArray() throws IOException {
    String json = "[{\"a\":\"b\",\"c\":1},123]";
    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .headers(Collections.emptyMap())
        .body(json, UTF_8)
        .request(request)
        .build();
    assertThat(jsonArray.similar(new JsonDecoder().decode(response, JSONArray.class))).isTrue();
  }

  @Test
  void decodesObject() throws IOException {
    String json = "{\"a\":\"b\",\"c\":1}";
    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .headers(Collections.emptyMap())
        .body(json, UTF_8)
        .request(request)
        .build();
    assertThat(jsonObject.similar(new JsonDecoder().decode(response, JSONObject.class))).isTrue();
  }

  @Test
  void decodesString() throws IOException {
    String json = "qwerty";
    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .headers(Collections.emptyMap())
        .body(json, UTF_8)
        .request(request)
        .build();
    assertThat(new JsonDecoder().decode(response, String.class)).isEqualTo("qwerty");
  }

  @Test
  void notFoundDecodesToEmpty() throws IOException {
    Response response = Response.builder()
        .status(404)
        .reason("Not found")
        .headers(Collections.emptyMap())
        .request(request)
        .build();
    assertThat(((JSONObject) new JsonDecoder().decode(response, JSONObject.class)).isEmpty())
        .isTrue();
  }

  @Test
  void nullBodyDecodesToEmpty() throws IOException {
    Response response = Response.builder()
        .status(204)
        .reason("OK")
        .headers(Collections.emptyMap())
        .request(request)
        .build();
    assertThat(((JSONObject) new JsonDecoder().decode(response, JSONObject.class)).isEmpty())
        .isTrue();
  }

  @Test
  void nullBodyDecodesToNullString() throws IOException {
    Response response = Response.builder()
        .status(204)
        .reason("OK")
        .headers(Collections.emptyMap())
        .request(request)
        .build();
    assertThat(new JsonDecoder().decode(response, String.class)).isNull();
  }

  @Test
  void emptyBodyDecodesToEmpty() throws IOException {
    Response response = Response.builder()
        .status(204)
        .reason("OK")
        .headers(Collections.emptyMap())
        .body("", UTF_8)
        .request(request)
        .build();
    assertThat(((JSONObject) new JsonDecoder().decode(response, JSONObject.class)).isEmpty())
        .isTrue();
  }

  @Test
  void unknownTypeThrowsDecodeException() throws IOException {
    String json = "[{\"a\":\"b\",\"c\":1},123]";
    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .headers(Collections.emptyMap())
        .body(json, UTF_8)
        .request(request)
        .build();
    Exception exception = assertThrows(DecodeException.class,
        () -> new JsonDecoder().decode(response, Clock.class));
    assertThat(exception.getMessage())
        .isEqualTo("class java.time.Clock is not a type supported by this decoder.");
  }

  @Test
  void badJsonThrowsWrappedJSONException() throws IOException {
    String json = "{\"a\":\"b\",\"c\":1}";
    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .headers(Collections.emptyMap())
        .body(json, UTF_8)
        .request(request)
        .build();
    Exception exception = assertThrows(DecodeException.class,
        () -> new JsonDecoder().decode(response, JSONArray.class));
    assertThat(exception.getMessage())
        .isEqualTo("A JSONArray text must start with '[' at 1 [character 2 line 1]");
    assertThat(exception.getCause() instanceof JSONException).isTrue();
  }

  @Test
  void causedByCommonException() throws IOException {
    Response.Body body = mock(Response.Body.class);
    when(body.asReader(any())).thenThrow(new JSONException("test exception",
        new Exception("test cause exception")));
    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .headers(Collections.emptyMap())
        .body(body)
        .request(request)
        .build();
    Exception exception = assertThrows(DecodeException.class,
        () -> new JsonDecoder().decode(response, JSONArray.class));
    assertThat(exception.getMessage()).isEqualTo("test exception");
  }

  @Test
  void causedByIOException() throws IOException {
    Response.Body body = mock(Response.Body.class);
    when(body.asReader(any())).thenThrow(new JSONException("test exception",
        new IOException("test cause exception")));
    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .headers(Collections.emptyMap())
        .body(body)
        .request(request)
        .build();
    Exception exception = assertThrows(IOException.class,
        () -> new JsonDecoder().decode(response, JSONArray.class));
    assertThat(exception.getMessage()).isEqualTo("test cause exception");
  }

  @Test
  void checkedException() throws IOException {
    Response.Body body = mock(Response.Body.class);
    when(body.asReader(any())).thenThrow(new IOException("test exception"));
    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .headers(Collections.emptyMap())
        .body(body)
        .request(request)
        .build();
    Exception exception = assertThrows(IOException.class,
        () -> new JsonDecoder().decode(response, JSONArray.class));
    assertThat(exception.getMessage()).isEqualTo("test exception");
  }

  @Test
  void decodesExtendedArray() throws IOException {
    String json = "[{\"a\":\"b\",\"c\":1},123]";
    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .headers(Collections.emptyMap())
        .body(json, UTF_8)
        .request(request)
        .build();
    assertThat(jsonArray.similar(new JsonDecoder().decode(response, ExtendedJSONArray.class)))
        .isTrue();
  }

  @Test
  void decodeExtendedObject() throws IOException {
    String json = "{\"a\":\"b\",\"c\":1}";
    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .headers(Collections.emptyMap())
        .body(json, UTF_8)
        .request(request)
        .build();
    assertThat(jsonObject.similar(new JsonDecoder().decode(response, ExtendedJSONObject.class)))
        .isTrue();
  }

  static class ExtendedJSONArray extends JSONArray {

  }

  static class ExtendedJSONObject extends JSONObject {

  }

}
