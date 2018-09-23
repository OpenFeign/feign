/**
 * Copyright 2012-2018 The Feign Authors
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
package feign.jackson.jaxb;

import static feign.Util.UTF_8;
import static feign.assertj.FeignAssertions.assertThat;
import feign.Request;
import feign.Request.HttpMethod;
import feign.RequestTemplate;
import feign.Response;
import feign.Util;
import java.util.Collections;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.junit.Test;

public class JacksonJaxbCodecTest {

  @Test
  public void encodeTest() {
    JacksonJaxbJsonEncoder encoder = new JacksonJaxbJsonEncoder();
    RequestTemplate template = new RequestTemplate();

    encoder.encode(new MockObject("Test"), MockObject.class, template);

    assertThat(template).hasBody("{\"value\":\"Test\"}");
  }

  @Test
  public void decodeTest() throws Exception {
    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .body("{\"value\":\"Test\"}", UTF_8)
        .build();
    JacksonJaxbJsonDecoder decoder = new JacksonJaxbJsonDecoder();

    assertThat(decoder.decode(response, MockObject.class))
        .isEqualTo(new MockObject("Test"));
  }

  /**
   * Enabled via {@link feign.Feign.Builder#decode404()}
   */
  @Test
  public void notFoundDecodesToEmpty() throws Exception {
    Response response = Response.builder()
        .status(404)
        .reason("NOT FOUND")
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .build();
    assertThat((byte[]) new JacksonJaxbJsonDecoder().decode(response, byte[].class)).isEmpty();
  }

  @XmlRootElement
  @XmlAccessorType(XmlAccessType.FIELD)
  static class MockObject {

    @XmlElement
    private String value;

    MockObject() {}

    MockObject(String value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof MockObject) {
        MockObject other = (MockObject) obj;
        return value.equals(other.value);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return value != null ? value.hashCode() : 0;
    }
  }
}
