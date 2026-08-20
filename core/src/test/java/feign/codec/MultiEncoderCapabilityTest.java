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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import feign.Capability;
import feign.Feign;
import feign.Headers;
import feign.RequestLine;
import feign.RequestTemplate;
import feign.Response;
import feign.Util;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** How {@link MultiEncoder} behaves once configured on a {@link Feign} builder. */
class MultiEncoderCapabilityTest {

  interface MixedApi {
    @RequestLine("POST /json")
    @Headers("Content-Type: application/json")
    void json(String body);

    @RequestLine("POST /xml")
    @Headers("Content-Type: application/xml")
    void xml(String body);
  }

  static class TaggingEncoder implements Encoder {
    private final String tag;

    TaggingEncoder(String tag) {
      this.tag = tag;
    }

    @Override
    public void encode(Object object, Type bodyType, RequestTemplate template) {
      template.body(tag);
    }
  }

  /** A capability that wraps the encoder, the way the metrics modules do. */
  public static class CountingCapability implements Capability {
    int wrapped;
    int encodeCalls;

    @Override
    public Encoder enrich(Encoder encoder) {
      wrapped++;
      return (object, bodyType, template) -> {
        encodeCalls++;
        encoder.encode(object, bodyType, template);
      };
    }
  }

  private static MixedApi target(Feign.Builder builder, AtomicReference<String> captured) {
    return builder
        .client(
            (request, options) -> {
              captured.set(new String(request.body(), Util.UTF_8));
              return Response.builder()
                  .status(200)
                  .reason("OK")
                  .request(request)
                  .headers(Collections.emptyMap())
                  .body("", Util.UTF_8)
                  .build();
            })
        .target(MixedApi.class, "http://localhost:1");
  }

  private static RequestTemplate template(String contentType) {
    RequestTemplate template = new RequestTemplate();
    template.header("Content-Type", contentType);
    return template;
  }

  @Test
  void encodersOnTheBuilderRouteInTheOrderGiven() {
    AtomicReference<String> captured = new AtomicReference<>();

    MixedApi api =
        target(
            Feign.builder()
                .encoders(
                    PredicatedEncoder.of(
                        EncoderPredicate.jsonContentType(), new TaggingEncoder("json")),
                    PredicatedEncoder.of(EncoderPredicate.any(), new TaggingEncoder("fallback"))),
            captured);

    api.json("{}");
    assertThat(captured.get()).isEqualTo("json");

    api.xml("<x/>");
    assertThat(captured.get()).isEqualTo("fallback");
  }

  @Test
  void encodersOnTheBuilderFailWhenNothingAccepts() {
    MixedApi api =
        target(
            Feign.builder()
                .encoders(
                    PredicatedEncoder.of(
                        EncoderPredicate.jsonContentType(), new TaggingEncoder("json"))),
            new AtomicReference<>());

    assertThatThrownBy(() -> api.xml("<x/>"))
        .isInstanceOf(EncodeException.class)
        .hasMessageContaining("Unable to encode java.lang.String (Content-Type: application/xml)")
        .hasMessageContaining("TaggingEncoder when Content-Type is JSON");
  }

  @Test
  void capabilityWrapsTheCompositeAndRoutingStillWorks() {
    CountingCapability capability = new CountingCapability();
    AtomicReference<String> captured = new AtomicReference<>();

    MixedApi api =
        target(
            Feign.builder()
                .encoder(
                    MultiEncoder.builder()
                        .add(EncoderPredicate.jsonContentType(), new TaggingEncoder("json"))
                        .add(EncoderPredicate.xmlContentType(), new TaggingEncoder("xml"))
                        .add(EncoderPredicate.any(), new TaggingEncoder("fallback"))
                        .build())
                .addCapability(capability),
            captured);

    api.json("{}");
    assertThat(captured.get()).isEqualTo("json");

    api.xml("<x/>");
    assertThat(captured.get()).isEqualTo("xml");

    // the capability sees the MultiEncoder as one encoder, not one per delegate
    assertThat(capability.wrapped).isEqualTo(1);
    assertThat(capability.encodeCalls).isEqualTo(2);
  }

  /**
   * A wrapper that answers {@code canEncode} for itself instead of forwarding claims every request,
   * which is why the metrics modules' {@code MeteredEncoder} forwards it to its delegate.
   */
  @Test
  void wrappingWithoutForwardingCanEncodeErasesSelfDeclaration() {
    PredicatedEncoder jsonOnly =
        new PredicatedEncoder() {
          @Override
          public boolean canEncode(Object object, Type bodyType, RequestTemplate template) {
            return Util.isJsonContentType(template);
          }

          @Override
          public void encode(Object object, Type bodyType, RequestTemplate template) {
            template.body("json");
          }
        };

    PredicatedEncoder naive =
        new PredicatedEncoder() {
          @Override
          public boolean canEncode(Object object, Type bodyType, RequestTemplate template) {
            return true;
          }

          @Override
          public void encode(Object object, Type bodyType, RequestTemplate template) {
            jsonOnly.encode(object, bodyType, template);
          }
        };

    PredicatedEncoder forwarding =
        new PredicatedEncoder() {
          @Override
          public boolean canEncode(Object object, Type bodyType, RequestTemplate template) {
            return jsonOnly.canEncode(object, bodyType, template);
          }

          @Override
          public void encode(Object object, Type bodyType, RequestTemplate template) {
            jsonOnly.encode(object, bodyType, template);
          }
        };

    RequestTemplate naiveTemplate = template("application/xml");
    MultiEncoder.builder()
        .add(naive)
        .add(EncoderPredicate.any(), new TaggingEncoder("fallback"))
        .build()
        .encode("body", String.class, naiveTemplate);
    assertThat(naiveTemplate.requestBody().asString()).isEqualTo("json");

    RequestTemplate forwardedTemplate = template("application/xml");
    MultiEncoder.builder()
        .add(forwarding)
        .add(EncoderPredicate.any(), new TaggingEncoder("fallback"))
        .build()
        .encode("body", String.class, forwardedTemplate);
    assertThat(forwardedTemplate.requestBody().asString()).isEqualTo("fallback");
  }
}
