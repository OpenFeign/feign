/**
 * Copyright 2012-2021 The Feign Authors
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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import feign.FeignException;
import feign.Request;
import feign.Request.HttpMethod;
import feign.Response;
import feign.Util;

public class FifoDecoderTest {

  public class FakeDecoder implements TypedDecoder {

    @Override
    public Object decode(Response response, Type type)
        throws IOException, DecodeException, FeignException {
      return null;
    }

    @Override
    public boolean canDecode(Response response, Type type)
        throws IOException, DecodeException, FeignException {
      return false;
    }

  }


  public class FixResponseDecoder implements TypedDecoder {

    @Override
    public Object decode(Response response, Type type)
        throws IOException, DecodeException, FeignException {
      return "fix response";
    }

    @Override
    public boolean canDecode(Response response, Type type)
        throws IOException, DecodeException, FeignException {
      return true;
    }

  }

  @Test
  public void defaultDecoderWorks() throws DecodeException, FeignException, IOException {
    Response response = knownResponse();

    TypedDecoder decoder1 = new FakeDecoder();
    TypedDecoder decoder2 = new FakeDecoder();
    FifoDecoder decoder = new FifoDecoder()
        .append(decoder1)
        .append(decoder2);

    Object decodedObject = decoder.decode(response, String.class);
    assertEquals(String.class, decodedObject.getClass());
    assertEquals("response body", decodedObject.toString());
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

  @Test
  public void matchingDecoderWorks() throws DecodeException, FeignException, IOException {
    Response response = knownResponse();

    TypedDecoder decoder1 = new FixResponseDecoder();
    FifoDecoder decoder = new FifoDecoder(new Decoder() {

      @Override
      public Object decode(Response response, Type type)
          throws IOException, DecodeException, FeignException {
        throw new IllegalStateException("Should never be executed");
      }
    })
        .append(decoder1);

    Object decodedObject = decoder.decode(response, String.class);
    assertEquals(String.class, decodedObject.getClass());
    assertEquals("fix response", decodedObject.toString());
  }

}
