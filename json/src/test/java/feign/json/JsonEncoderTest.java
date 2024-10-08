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

import static feign.Util.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import feign.RequestTemplate;
import feign.codec.EncodeException;
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
        "[{\"a\":\"b\",\"c\":1},123]", new String(requestTemplate.body(), UTF_8), false);
  }

  @Test
  void encodesObject() {
    new JsonEncoder().encode(jsonObject, JSONObject.class, requestTemplate);
    JSONAssert.assertEquals(
        "{\"a\":\"b\",\"c\":1}", new String(requestTemplate.body(), UTF_8), false);
  }

  @Test
  void encodesNull() {
    new JsonEncoder().encode(null, JSONObject.class, new RequestTemplate());
    assertThat(requestTemplate.body()).isNull();
  }

  @Test
  void unknownTypeThrowsEncodeException() {
    Exception exception =
        assertThrows(
            EncodeException.class,
            () -> new JsonEncoder().encode("qwerty", Clock.class, new RequestTemplate()));
    assertThat(exception.getMessage())
        .isEqualTo("class java.time.Clock is not a type supported by this encoder.");
  }
}
