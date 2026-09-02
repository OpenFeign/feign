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
import feign.Param;
import feign.Request;
import feign.Request.HttpMethod;
import feign.RequestLine;
import feign.Response;
import feign.Util;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** How {@link MultiDecoder} behaves end to end and when a {@link Capability} wraps the decoder. */
class MultiDecoderCapabilityTest {

  interface MixedApi {
    @RequestLine("GET /{path}")
    String get(@Param("path") String path);
  }

  static class TaggingDecoder implements Decoder {
    private final String tag;

    TaggingDecoder(String tag) {
      this.tag = tag;
    }

    @Override
    public Object decode(Response response, Type type) {
      return tag;
    }
  }

  /** A capability that wraps the decoder, the way the metrics modules do. */
  public static class CountingCapability implements Capability {
    int wrapped;
    int decodeCalls;

    @Override
    public Decoder enrich(Decoder decoder) {
      wrapped++;
      return (response, type) -> {
        decodeCalls++;
        return decoder.decode(response, type);
      };
    }
  }

  private static Response response(String contentType, String body) {
    return response(
        contentType,
        body,
        Request.create(HttpMethod.GET, "http://localhost:1/", Collections.emptyMap(), null, null));
  }

  private static Response response(String contentType, String body, Request request) {
    Map<String, Collection<String>> headers = new HashMap<>();
    headers.put("Content-Type", Collections.singletonList(contentType));
    return Response.builder()
        .status(200)
        .reason("OK")
        .headers(headers)
        .body(body, Util.UTF_8)
        .request(request)
        .build();
  }

  private static MixedApi target(Feign.Builder builder, Map<String, String> contentTypes) {
    return builder
        .client(
            (request, options) -> {
              String path = request.url().substring(request.url().lastIndexOf('/') + 1);
              return response(contentTypes.get(path), "payload", request);
            })
        .target(MixedApi.class, "http://localhost:1");
  }

  @Test
  void capabilityWrapsTheCompositeAndRoutingStillWorks() {
    CountingCapability capability = new CountingCapability();
    Map<String, String> contentTypes = new HashMap<>();
    contentTypes.put("json", "application/json");
    contentTypes.put("xml", "application/xml");

    MixedApi api =
        target(
            Feign.builder()
                .decoder(
                    MultiDecoder.builder()
                        .add(DecoderPredicate.jsonContentType(), new TaggingDecoder("json"))
                        .add(DecoderPredicate.xmlContentType(), new TaggingDecoder("xml"))
                        .add(DecoderPredicate.any(), new TaggingDecoder("fallback"))
                        .build())
                .addCapability(capability),
            contentTypes);

    assertThat(api.get("json")).isEqualTo("json");
    assertThat(api.get("xml")).isEqualTo("xml");

    // the capability sees the MultiDecoder as one decoder, not one per delegate
    assertThat(capability.wrapped).isEqualTo(1);
    assertThat(capability.decodeCalls).isEqualTo(2);
  }

  @Test
  void decodersOnTheBuilderRouteInTheOrderGiven() {
    Map<String, String> contentTypes = new HashMap<>();
    contentTypes.put("json", "application/json");
    contentTypes.put("csv", "text/csv");

    MixedApi api =
        target(
            Feign.builder()
                .decoders(
                    new SelfDeclaringJsonDecoder(),
                    PredicatedDecoder.of(DecoderPredicate.any(), new TaggingDecoder("fallback"))),
            contentTypes);

    assertThat(api.get("json")).isEqualTo("json");
    assertThat(api.get("csv")).isEqualTo("fallback");
  }

  @Test
  void decodersOnTheBuilderFailWhenNothingAccepts() {
    Map<String, String> contentTypes = new HashMap<>();
    contentTypes.put("csv", "text/csv");

    MixedApi api = target(Feign.builder().decoders(new SelfDeclaringJsonDecoder()), contentTypes);

    assertThatThrownBy(() -> api.get("csv"))
        .isInstanceOf(DecodeException.class)
        .hasMessageContaining("Unable to decode 200 response (Content-Type: text/csv)")
        .hasMessageContaining("SelfDeclaringJsonDecoder");
  }

  /** The selected decoder still receives an unread body: predicates must not consume it. */
  @Test
  void predicatesLeaveTheBodyForTheSelectedDecoder() throws IOException {
    Decoder decoder =
        MultiDecoder.builder()
            .add(
                DecoderPredicate.jsonContentType(),
                (response, type) -> Util.toString(response.body().asReader(Util.UTF_8)))
            .build();

    assertThat(decoder.decode(response("application/json", "payload"), String.class))
        .isEqualTo("payload");
  }

  static class SelfDeclaringJsonDecoder implements Decoder, PredicatedDecoder {

    @Override
    public Object decode(Response response, Type type) {
      return "json";
    }

    @Override
    public boolean canDecode(Response response, Type type) {
      return Util.isJsonContentType(response);
    }
  }

  /**
   * A wrapper that answers {@code canDecode} for itself instead of forwarding claims every
   * response, which is why the metrics modules' {@code MeteredDecoder} forwards it to its delegate.
   */
  @Test
  void wrappingWithoutForwardingCanDecodeErasesSelfDeclaration() throws IOException {
    PredicatedDecoder jsonOnly = new SelfDeclaringJsonDecoder();

    PredicatedDecoder naive =
        new PredicatedDecoder() {
          @Override
          public boolean canDecode(Response response, Type type) {
            return true;
          }

          @Override
          public Object decode(Response response, Type type) throws IOException {
            return jsonOnly.decode(response, type);
          }
        };

    PredicatedDecoder forwarding =
        new PredicatedDecoder() {
          @Override
          public boolean canDecode(Response response, Type type) {
            return jsonOnly.canDecode(response, type);
          }

          @Override
          public Object decode(Response response, Type type) throws IOException {
            return jsonOnly.decode(response, type);
          }
        };

    assertThat(
            MultiDecoder.builder()
                .add(naive)
                .add(DecoderPredicate.any(), new TaggingDecoder("fallback"))
                .build()
                .decode(response("application/xml", "payload"), String.class))
        .isEqualTo("json");

    assertThat(
            MultiDecoder.builder()
                .add(forwarding)
                .add(DecoderPredicate.any(), new TaggingDecoder("fallback"))
                .build()
                .decode(response("application/xml", "payload"), String.class))
        .isEqualTo("fallback");
  }
}
