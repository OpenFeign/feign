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

import feign.RequestTemplate;
import feign.codec.EncodeException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import java.util.Date;
import static feign.Util.UTF_8;
import static org.junit.Assert.*;

public class JsonEncoderTest {

  private JSONArray jsonArray;
  private JSONObject jsonObject;
  private RequestTemplate requestTemplate;

  @Before
  public void setUp() {
    jsonObject = new JSONObject();
    jsonObject.put("a", "b");
    jsonObject.put("c", 1);
    jsonArray = new JSONArray();
    jsonArray.put(jsonObject);
    jsonArray.put(123);
    requestTemplate = new RequestTemplate();
  }

  @Test
  public void encodesArray() {
    new JsonEncoder().encode(jsonArray, JSONArray.class, requestTemplate);
    JSONAssert.assertEquals("[{\"a\":\"b\",\"c\":1},123]",
        new String(requestTemplate.body(), UTF_8), false);
  }

  @Test
  public void encodesObject() {
    new JsonEncoder().encode(jsonObject, JSONObject.class, requestTemplate);
    JSONAssert.assertEquals("{\"a\":\"b\",\"c\":1}", new String(requestTemplate.body(), UTF_8),
        false);
  }

  @Test
  public void encodesNull() {
    new JsonEncoder().encode(null, JSONObject.class, new RequestTemplate());
    assertNull(requestTemplate.body());
  }

  @Test
  public void unknownTypeThrowsEncodeException() {
    Exception exception = assertThrows(EncodeException.class,
        () -> new JsonEncoder().encode("qwerty", Date.class, new RequestTemplate()));
    assertEquals("class java.util.Date is not a type supported by this encoder.",
        exception.getMessage());
  }

}
