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
package feign.json;

import static org.assertj.core.api.Assertions.*;

import feign.Request;
import feign.RequestTemplate;
import feign.codec.EncodeException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

class JsonEncoderTest {

  private JSONArray jsonArray;
  private JSONObject jsonObject;
  private RequestTemplate requestTemplate;

  @BeforeEach
  void setUp() {
    jsonObject = new JSONObject();
    jsonObject.put("a", "b");
    jsonObject.put("c", 1);
    jsonArray = new JSONArray();
    jsonArray.put(jsonObject);
    jsonArray.put(123);
    requestTemplate = new RequestTemplate();
  }

  @Test
  void encodesArray() {
    new JsonEncoder().encode(jsonArray, JSONArray.class, requestTemplate);
    JSONAssert.assertEquals(
        "[{\"a\":\"b\",\"c\":1},123]",
        requestTemplate.requestBody().map(this::bodyAsUtf8String).orElse(null),
        false);
  }

  @Test
  void encodesObject() {
    new JsonEncoder().encode(jsonObject, JSONObject.class, requestTemplate);
    JSONAssert.assertEquals(
        "{\"a\":\"b\",\"c\":1}",
        requestTemplate.requestBody().map(this::bodyAsUtf8String).orElse(null),
        false);
  }

  @Test
  void encodesNull() {
    new JsonEncoder().encode(null, JSONObject.class, new RequestTemplate());
    assertThat(requestTemplate.requestBody()).isEmpty();
  }

  @Test
  void unknownTypeThrowsEncodeException() {
    Exception exception =
        assertThatExceptionOfType(EncodeException.class)
            .isThrownBy(
                () -> new JsonEncoder().encode("qwerty", Clock.class, new RequestTemplate()))
            .actual();
    assertThat(exception.getMessage())
        .isEqualTo("class java.time.Clock is not a type supported by this encoder.");
  }

  private String bodyAsUtf8String(Request.Body body) {
    try {
      return body.writeToString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new AssertionError("Failed to write body", e);
    }
  }
}
