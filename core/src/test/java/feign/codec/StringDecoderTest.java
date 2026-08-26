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
package feign.codec;

import static feign.Util.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import feign.Request;
import feign.Request.HttpMethod;
import feign.Response;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class StringDecoderTest {

  private final StringDecoder decoder = new StringDecoder();

  @Test
  void declaresTheTypesItDecodes() {
    assertThat(decoder.canDecode(knownResponse(200), String.class)).isTrue();
    assertThat(decoder.canDecode(knownResponse(200), byte[].class)).isFalse();
    assertThat(decoder.canDecode(knownResponse(200), Document.class)).isFalse();
  }

  @Test
  void acceptsAnyTypeWhenThereIsNoBodyToRead() {
    assertThat(decoder.canDecode(nullBodyResponse(), Document.class)).isTrue();
    assertThat(decoder.canDecode(knownResponse(404), Document.class)).isTrue();
    assertThat(decoder.canDecode(knownResponse(204), Document.class)).isTrue();
  }

  @Test
  void decodesToString() throws Exception {
    assertThat(decoder.decode(knownResponse(200), String.class)).isEqualTo("response body");
  }

  private Response knownResponse(int status) {
    String content = "response body";
    InputStream inputStream = new ByteArrayInputStream(content.getBytes(UTF_8));
    Map<String, Collection<String>> headers = new HashMap<>();
    headers.put("Content-Type", Collections.singleton("text/plain"));
    return Response.builder()
        .status(status)
        .reason("OK")
        .headers(headers)
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, UTF_8))
        .body(inputStream, content.length())
        .build();
  }

  private Response nullBodyResponse() {
    return Response.builder()
        .status(200)
        .reason("OK")
        .headers(Collections.<String, Collection<String>>emptyMap())
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, UTF_8))
        .build();
  }
}
