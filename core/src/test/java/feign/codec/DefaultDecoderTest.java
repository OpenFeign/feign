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
package feign.codec;

import static feign.Util.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import feign.Request.HttpMethod;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.w3c.dom.Document;
import feign.Request;
import feign.Response;
import feign.Util;

@SuppressWarnings("deprecation")
public class DefaultDecoderTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  private final Decoder decoder = new Decoder.Default();

  @Test
  public void testDecodesToString() throws Exception {
    Response response = knownResponse();
    Object decodedObject = decoder.decode(response, String.class);
    assertEquals(String.class, decodedObject.getClass());
    assertEquals("response body", decodedObject.toString());
  }

  @Test
  public void testDecodesToByteArray() throws Exception {
    Response response = knownResponse();
    Object decodedObject = decoder.decode(response, byte[].class);
    assertEquals(byte[].class, decodedObject.getClass());
    assertEquals("response body", new String((byte[]) decodedObject, UTF_8));
  }

  @Test
  public void testDecodesNullBodyToNull() throws Exception {
    assertNull(decoder.decode(nullBodyResponse(), Document.class));
  }

  @Test
  public void testRefusesToDecodeOtherTypes() throws Exception {
    thrown.expect(DecodeException.class);
    thrown.expectMessage(" is not a type supported by this decoder.");

    decoder.decode(knownResponse(), Document.class);
  }

  private Response knownResponse() {
    String content = "response body";
    InputStream inputStream = new ByteArrayInputStream(content.getBytes(UTF_8));
    Map<String, Collection<String>> headers = new HashMap<String, Collection<String>>();
    headers.put("Content-Type", Collections.singleton("text/plain"));
    return Response.builder()
        .status(200)
        .reason("OK")
        .headers(headers)
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .body(inputStream, content.length())
        .build();
  }

  private Response nullBodyResponse() {
    return Response.builder()
        .status(200)
        .reason("OK")
        .headers(Collections.<String, Collection<String>>emptyMap())
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .build();
  }
}
