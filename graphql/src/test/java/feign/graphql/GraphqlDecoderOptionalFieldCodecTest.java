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
package feign.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Feign;
import feign.Headers;
import feign.codec.JsonCodec;
import feign.jackson.JacksonCodec;
import feign.jackson3.Jackson3Codec;
import feign.mock.HttpMethod;
import feign.mock.MockClient;
import feign.mock.MockTarget;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

// Gson, Moshi, and Fastjson2 don't support Optional<T> fields in records
class GraphqlDecoderOptionalFieldCodecTest {

  public record Item(String name, Optional<String> step) {}

  @Headers("Content-Type: application/json")
  interface ItemApi {

    @GraphqlQuery("query { item(id: \"1\") { name step } }")
    Item getItem();
  }

  static Stream<Arguments> codecs() {
    var jackson =
        new JacksonCodec(
            new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .findAndRegisterModules());

    var jackson3 =
        new Jackson3Codec(
            JsonMapper.builder()
                .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build());

    return Stream.of(Arguments.of("jackson", jackson), Arguments.of("jackson3", jackson3));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("codecs")
  void stepExplicitNull(String name, JsonCodec codec) {
    var mockClient = new MockClient();
    mockClient.ok(HttpMethod.POST, "/", "{\"data\":{\"item\":{\"name\":\"test\",\"step\":null}}}");

    var api = buildClient(mockClient, codec);
    var item = api.getItem();

    assertThat(item.name()).isEqualTo("test");
    assertThat(item.step()).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("codecs")
  void stepMissing(String name, JsonCodec codec) {
    var mockClient = new MockClient();
    mockClient.ok(HttpMethod.POST, "/", "{\"data\":{\"item\":{\"name\":\"test\"}}}");

    var api = buildClient(mockClient, codec);
    var item = api.getItem();

    assertThat(item.name()).isEqualTo("test");
    assertThat(item.step()).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("codecs")
  void stepPresent(String name, JsonCodec codec) {
    var mockClient = new MockClient();
    mockClient.ok(
        HttpMethod.POST, "/", "{\"data\":{\"item\":{\"name\":\"test\",\"step\":\"foo\"}}}");

    var api = buildClient(mockClient, codec);
    var item = api.getItem();

    assertThat(item.name()).isEqualTo("test");
    assertThat(item.step()).isPresent().hasValue("foo");
  }

  private ItemApi buildClient(MockClient mockClient, JsonCodec codec) {
    return Feign.builder()
        .addCapability(new GraphqlCapability(codec))
        .client(mockClient)
        .target(new MockTarget<>(ItemApi.class));
  }
}
