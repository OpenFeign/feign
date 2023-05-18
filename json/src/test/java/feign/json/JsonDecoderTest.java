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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import feign.Request;
import feign.Response;
import feign.codec.DecodeException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;

public class JsonDecoderTest {

  private static JSONArray jsonArray;
  private static JSONObject jsonObject;
  private static Request request;

  @BeforeClass
  public static void setUpClass() {
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
  public void decodesArray() throws IOException {
    String json = "[{\"a\":\"b\",\"c\":1},123]";
    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .headers(Collections.emptyMap())
        .body(json, UTF_8)
        .request(request)
        .build();
    assertTrue(jsonArray.similar(new JsonDecoder().decode(response, JSONArray.class)));
  }

  @Test
  public void decodesObject() throws IOException {
    String json = "{\"a\":\"b\",\"c\":1}";
    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .headers(Collections.emptyMap())
        .body(json, UTF_8)
        .request(request)
        .build();
    assertTrue(jsonObject.similar(new JsonDecoder().decode(response, JSONObject.class)));
  }

  @Test
  public void decodesString() throws IOException {
    String json = "qwerty";
    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .headers(Collections.emptyMap())
        .body(json, UTF_8)
        .request(request)
        .build();
    assertEquals("qwerty", new JsonDecoder().decode(response, String.class));
  }

  @Test
  public void notFoundDecodesToEmpty() throws IOException {
    Response response = Response.builder()
        .status(404)
        .reason("Not found")
        .headers(Collections.emptyMap())
        .request(request)
        .build();
    assertTrue(((JSONObject) new JsonDecoder().decode(response, JSONObject.class)).isEmpty());
  }

  @Test
  public void nullBodyDecodesToEmpty() throws IOException {
    Response response = Response.builder()
        .status(204)
        .reason("OK")
        .headers(Collections.emptyMap())
        .request(request)
        .build();
    assertTrue(((JSONObject) new JsonDecoder().decode(response, JSONObject.class)).isEmpty());
  }

  @Test
  public void nullBodyDecodesToNullString() throws IOException {
    Response response = Response.builder()
        .status(204)
        .reason("OK")
        .headers(Collections.emptyMap())
        .request(request)
        .build();
    assertNull(new JsonDecoder().decode(response, String.class));
  }

  @Test
  public void emptyBodyDecodesToEmpty() throws IOException {
    Response response = Response.builder()
        .status(204)
        .reason("OK")
        .headers(Collections.emptyMap())
        .body("", UTF_8)
        .request(request)
        .build();
    assertTrue(((JSONObject) new JsonDecoder().decode(response, JSONObject.class)).isEmpty());
  }

  @Test
  public void unknownTypeThrowsDecodeException() throws IOException {
    String json = "[{\"a\":\"b\",\"c\":1},123]";
    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .headers(Collections.emptyMap())
        .body(json, UTF_8)
        .request(request)
        .build();
    Exception exception = assertThrows(DecodeException.class,
        () -> new JsonDecoder().decode(response, Date.class));
    assertEquals("class java.util.Date is not a type supported by this decoder.",
        exception.getMessage());
  }

  @Test
  public void badJsonThrowsWrappedJSONException() throws IOException {
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
    assertEquals("A JSONArray text must start with '[' at 1 [character 2 line 1]",
        exception.getMessage());
    assertTrue(exception.getCause() instanceof JSONException);
  }

  @Test
  public void causedByCommonException() throws IOException {
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
    assertEquals("test exception", exception.getMessage());
  }

  @Test
  public void causedByIOException() throws IOException {
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
    assertEquals("test cause exception", exception.getMessage());
  }

  @Test
  public void checkedException() throws IOException {
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
    assertEquals("test exception", exception.getMessage());
  }

  @Test
  public void decodesExtendedArray() throws IOException {
    String json = "[{\"a\":\"b\",\"c\":1},123]";
    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .headers(Collections.emptyMap())
        .body(json, UTF_8)
        .request(request)
        .build();
    assertTrue(jsonArray.similar(new JsonDecoder().decode(response, ExtendedJSONArray.class)));
  }

  @Test
  public void decodeExtendedObject() throws IOException {
    String json = "{\"a\":\"b\",\"c\":1}";
    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .headers(Collections.emptyMap())
        .body(json, UTF_8)
        .request(request)
        .build();
    assertTrue(jsonObject.similar(new JsonDecoder().decode(response, ExtendedJSONObject.class)));
  }

  static class ExtendedJSONArray extends JSONArray {

  }

  static class ExtendedJSONObject extends JSONObject {

  }

}
