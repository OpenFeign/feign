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
package feign.jdkjson;

import static feign.Util.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import feign.Feign;
import feign.Param;
import feign.Request;
import feign.RequestLine;
import feign.codec.DecodeException;
import feign.codec.EncodeException;
import feign.mock.HttpMethod;
import feign.mock.MockClient;
import feign.mock.MockTarget;
import java.util.List;
import java.util.Map;
import jdk.incubator.json.JsonArray;
import jdk.incubator.json.JsonNumber;
import jdk.incubator.json.JsonObject;
import jdk.incubator.json.JsonParseException;
import jdk.incubator.json.JsonString;
import jdk.incubator.json.JsonValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdkJsonCodecTest {

  interface GitHub {

    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    JsonArray contributors(@Param("owner") String owner, @Param("repo") String repo);

    @RequestLine("GET /repos/{owner}/{repo}")
    JsonObject repo(@Param("owner") String owner, @Param("repo") String repo);

    @RequestLine("GET /repos/{owner}/{repo}")
    JsonValue repoValue(@Param("owner") String owner, @Param("repo") String repo);

    @RequestLine("GET /repos/{owner}/{repo}")
    String repoRaw(@Param("owner") String owner, @Param("repo") String repo);

    @RequestLine("GET /repos/{owner}/{repo}")
    Map<String, Object> repoMap(@Param("owner") String owner, @Param("repo") String repo);

    @RequestLine("POST /repos/{owner}/{repo}/contributors")
    JsonObject create(
        @Param("owner") String owner, @Param("repo") String repo, JsonValue contributor);

    @RequestLine("POST /repos/{owner}/{repo}/contributors")
    void createRaw(@Param("owner") String owner, @Param("repo") String repo, Object contributor);
  }

  private MockClient mockClient;
  private GitHub github;

  @BeforeEach
  void setUp() {
    mockClient = new MockClient();
    github =
        Feign.builder()
            .decoder(new JdkJsonDecoder())
            .encoder(new JdkJsonEncoder())
            .dismiss404()
            .client(mockClient)
            .target(new MockTarget<>(GitHub.class));
  }

  @Test
  void decodesArray() {
    mockClient.ok(
        HttpMethod.GET,
        "/repos/openfeign/feign/contributors",
        "[{\"login\":\"velo\",\"contributions\":42},{\"login\":\"kdavisk6\",\"contributions\":7}]");

    JsonArray contributors = github.contributors("openfeign", "feign");

    assertThat(contributors.asList()).hasSize(2);
    assertThat(contributors.get(0).get("login").asString()).isEqualTo("velo");
    assertThat(contributors.get(1).get("contributions").asInt()).isEqualTo(7);
  }

  @Test
  void decodesObject() {
    mockClient.ok(HttpMethod.GET, "/repos/openfeign/feign", "{\"name\":\"feign\",\"stars\":9600}");

    JsonObject repo = github.repo("openfeign", "feign");

    assertThat(repo.get("name").asString()).isEqualTo("feign");
    assertThat(repo.get("stars").asLong()).isEqualTo(9600L);
  }

  @Test
  void decodesAnyJsonValue() {
    mockClient.ok(HttpMethod.GET, "/repos/openfeign/feign", "\"feign\"");

    JsonValue value = github.repoValue("openfeign", "feign");

    assertThat(value).isInstanceOf(JsonString.class);
    assertThat(value.asString()).isEqualTo("feign");
  }

  @Test
  void decodesRawString() {
    mockClient.ok(HttpMethod.GET, "/repos/openfeign/feign", "{ \"name\" : \"feign\" }");

    assertThat(github.repoRaw("openfeign", "feign")).isEqualTo("{ \"name\" : \"feign\" }");
  }

  @Test
  void notFoundDecodesToNull() {
    mockClient.add(HttpMethod.GET, "/repos/openfeign/feign", 404);

    assertThat(github.repo("openfeign", "feign")).isNull();
  }

  @Test
  void emptyBodyDecodesToNull() {
    mockClient.ok(HttpMethod.GET, "/repos/openfeign/feign", "");

    assertThat(github.repo("openfeign", "feign")).isNull();
  }

  @Test
  void malformedJsonFailsWithParseCause() {
    mockClient.ok(HttpMethod.GET, "/repos/openfeign/feign", "{\"name\":");

    assertThatThrownBy(() -> github.repo("openfeign", "feign"))
        .isInstanceOf(DecodeException.class)
        .hasCauseInstanceOf(JsonParseException.class);
  }

  @Test
  void duplicateMemberNamesAreRejected() {
    mockClient.ok(HttpMethod.GET, "/repos/openfeign/feign", "{\"name\":\"a\",\"name\":\"b\"}");

    assertThatThrownBy(() -> github.repo("openfeign", "feign"))
        .isInstanceOf(DecodeException.class)
        .hasCauseInstanceOf(JsonParseException.class);
  }

  @Test
  void mismatchedJsonTypeFails() {
    mockClient.ok(HttpMethod.GET, "/repos/openfeign/feign", "[1,2]");

    assertThatThrownBy(() -> github.repo("openfeign", "feign"))
        .isInstanceOf(DecodeException.class)
        .hasMessage("Expected JsonObject but got [1,2]");
  }

  @Test
  void unsupportedReturnTypeFails() {
    mockClient.ok(HttpMethod.GET, "/repos/openfeign/feign", "{}");

    assertThatThrownBy(() -> github.repoMap("openfeign", "feign"))
        .isInstanceOf(DecodeException.class)
        .hasMessageContaining("is not a type supported by this decoder");
  }

  @Test
  void encodesJsonValue() {
    mockClient.ok(HttpMethod.POST, "/repos/openfeign/feign/contributors", "{\"id\":1}");
    JsonObject contributor =
        JsonObject.of(
            Map.of(
                "login", JsonString.of("velo"),
                "repos", JsonArray.of(List.of(JsonNumber.of(1), JsonNumber.of(2)))));

    JsonObject created = github.create("openfeign", "feign", contributor);

    Request request = mockClient.verifyOne(HttpMethod.POST, "/repos/openfeign/feign/contributors");
    assertThat(new String(request.body(), UTF_8)).isEqualTo(contributor.toString());
    assertThat(created.get("id").asInt()).isEqualTo(1);
  }

  @Test
  void unsupportedBodyTypeFails() {
    assertThatThrownBy(() -> github.createRaw("openfeign", "feign", Map.of("login", "velo")))
        .isInstanceOf(EncodeException.class)
        .hasMessageContaining("is not a type supported by this encoder");
  }
}
