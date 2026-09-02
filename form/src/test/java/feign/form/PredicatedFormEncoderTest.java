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
package feign.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import feign.Request;
import feign.RequestTemplate;
import feign.Util;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.codec.EncoderPredicate;
import feign.codec.MultiEncoder;
import feign.codec.PredicatedEncoder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PredicatedFormEncoderTest {

  private static RequestTemplate template(String contentType) {
    RequestTemplate template = new RequestTemplate();
    if (contentType != null) {
      template.header("Content-Type", contentType);
    }
    return template;
  }

  private static String bodyOf(RequestTemplate template) {
    try {
      return template.requestBody().get().writeToString(Util.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Map<String, Object> data() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("foo", "bar");
    return data;
  }

  @Test
  void acceptsFormRequests() {
    PredicatedEncoder encoder = FormEncoder.createPredicatedFormEncoder();

    assertThat(
            encoder.canEncode(
                data(), Map.class, template("application/x-www-form-urlencoded; charset=utf-8")))
        .isTrue();
    assertThat(encoder.canEncode(data(), Map.class, template("multipart/form-data"))).isTrue();
  }

  @Test
  void leavesEverythingElseToTheOtherEncoders() {
    PredicatedEncoder encoder = FormEncoder.createPredicatedFormEncoder();

    assertThat(encoder.canEncode("body", String.class, template("application/json"))).isFalse();
    assertThat(encoder.canEncode(data(), Map.class, template(null))).isFalse();
    assertThat(encoder.canEncode("body", String.class, template("multipart/form-data"))).isFalse();
  }

  @Test
  void encodesTheFormItAccepted() {
    RequestTemplate template = template("application/x-www-form-urlencoded");

    FormEncoder.createPredicatedFormEncoder().encode(data(), Map.class, template);

    assertThat(bodyOf(template)).isEqualTo("foo=bar");
  }

  @Test
  void routesAlongsideOtherEncoders() {
    Encoder json = (object, bodyType, template) -> template.body(Request.Body.of("json"));

    Encoder encoder =
        MultiEncoder.builder()
            .add(FormEncoder.createPredicatedFormEncoder())
            .add(EncoderPredicate.jsonContentType(), json)
            .build();

    RequestTemplate form = template("application/x-www-form-urlencoded");
    encoder.encode(data(), Map.class, form);
    assertThat(bodyOf(form)).isEqualTo("foo=bar");

    RequestTemplate other = template("application/json");
    encoder.encode("body", String.class, other);
    assertThat(bodyOf(other)).isEqualTo("json");
  }

  @Test
  void withoutADelegateAnythingItCannotEncodeFails() {
    RequestTemplate template = template("application/x-www-form-urlencoded");

    assertThatThrownBy(() -> new FormEncoder(null).encode("body", String.class, template))
        .isInstanceOf(EncodeException.class)
        .hasMessageContaining("This form encoder has no delegate encoder");
  }
}
