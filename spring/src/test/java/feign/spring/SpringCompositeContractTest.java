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
package feign.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import feign.CompositeContract;
import feign.Contract;
import feign.DefaultContract;
import feign.Feign;
import feign.Headers;
import feign.MethodMetadata;
import feign.Param;
import feign.Request;
import feign.RequestLine;
import feign.mock.HttpMethod;
import feign.mock.MockClient;
import feign.mock.MockTarget;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/** {@link CompositeContract} over Feign's default annotations and Spring MVC ones. */
class SpringCompositeContractTest {

  /** Half migrated from Feign annotations to Spring MVC ones. */
  @Headers("X-Client: feign")
  interface MixedStyleApi {

    @RequestLine("GET /legacy/{id}?verbose={verbose}")
    String legacy(@Param("id") String id, @Param("verbose") boolean verbose);

    @GetMapping("/modern/{id}")
    String modern(@PathVariable("id") String id, @RequestParam("verbose") boolean verbose);

    @PostMapping("/modern")
    void create(@RequestBody String body);

    @RequestLine("GET /mixed/{id}")
    String mixedParameters(@Param("id") String id, @RequestHeader("X-Trace") String trace);
  }

  @Test
  void callsMethodsDeclaredInEitherStyle() {
    MockClient mockClient =
        new MockClient()
            .ok(HttpMethod.GET, "/legacy/1?verbose=true", "legacy")
            .ok(HttpMethod.GET, "/modern/2?verbose=false", "modern")
            .noContent(HttpMethod.POST, "/modern")
            .ok(HttpMethod.GET, "/mixed/3", "mixed");

    MixedStyleApi api =
        Feign.builder()
            .contract(new CompositeContract(new DefaultContract(), new SpringContract()))
            .client(mockClient)
            .target(new MockTarget<>(MixedStyleApi.class));

    assertThat(api.legacy("1", true)).isEqualTo("legacy");
    assertThat(api.modern("2", false)).isEqualTo("modern");
    api.create("hello");
    assertThat(api.mixedParameters("3", "abc")).isEqualTo("mixed");

    Request legacy = mockClient.verifyOne(HttpMethod.GET, "/legacy/1?verbose=true");
    assertThat(legacy.headers()).containsEntry("X-Client", Collections.singletonList("feign"));

    Request modern = mockClient.verifyOne(HttpMethod.GET, "/modern/2?verbose=false");
    // class level @Headers is a Feign annotation, but it still applies to Spring style methods
    assertThat(modern.headers()).containsEntry("X-Client", Collections.singletonList("feign"));

    Request create = mockClient.verifyOne(HttpMethod.POST, "/modern");
    assertThat(create.headers()).containsEntry("X-Client", Collections.singletonList("feign"));
    assertThat(new String(create.body())).isEqualTo("hello");

    Request mixed = mockClient.verifyOne(HttpMethod.GET, "/mixed/3");
    assertThat(mixed.headers()).containsEntry("X-Trace", Collections.singletonList("abc"));
  }

  @Test
  void neitherContractAloneAcceptsMixedStyleApi() {
    assertThatThrownBy(() -> new DefaultContract().parseAndValidateMetadata(MixedStyleApi.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not annotated with HTTP method type");
    assertThatThrownBy(() -> new SpringContract().parseAndValidateMetadata(MixedStyleApi.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not annotated with HTTP method type");
  }

  @Test
  void orderDoesNotMatterWhenTheContractsShareNoAnnotations() {
    Contract defaultFirst = new CompositeContract(new DefaultContract(), new SpringContract());
    Contract springFirst = new CompositeContract(new SpringContract(), new DefaultContract());

    List<MethodMetadata> expected = defaultFirst.parseAndValidateMetadata(MixedStyleApi.class);
    List<MethodMetadata> actual = springFirst.parseAndValidateMetadata(MixedStyleApi.class);

    assertThat(actual).hasSameSizeAs(expected);
    for (int i = 0; i < expected.size(); i++) {
      assertThat(actual.get(i).configKey()).isEqualTo(expected.get(i).configKey());
      assertThat(actual.get(i).template().method()).isEqualTo(expected.get(i).template().method());
      assertThat(actual.get(i).template().url()).isEqualTo(expected.get(i).template().url());
      assertThat(actual.get(i).template().headers())
          .isEqualTo(expected.get(i).template().headers());
      assertThat(actual.get(i).indexToName()).isEqualTo(expected.get(i).indexToName());
      assertThat(actual.get(i).bodyIndex()).isEqualTo(expected.get(i).bodyIndex());
    }
  }

  /** Understands {@link GetMapping} like Spring does, but sends every call to {@code /v2}. */
  static class VersionedGetContract extends DefaultContract {
    VersionedGetContract() {
      registerMethodAnnotation(
          GetMapping.class,
          (mapping, data) -> {
            data.template().method(Request.HttpMethod.GET);
            data.template().uri("/v2" + mapping.value()[0]);
          });
    }
  }

  interface ModernApi {
    @GetMapping("/modern/{id}")
    String modern(@PathVariable("id") String id);
  }

  @Test
  void firstContractWinsWhenBothUnderstandTheSameAnnotation() {
    MethodMetadata springFirst =
        new CompositeContract(new SpringContract(), new VersionedGetContract())
            .parseAndValidateMetadata(ModernApi.class)
            .getFirst();
    assertThat(springFirst.template().url()).isEqualTo("/modern/{id}");

    MethodMetadata versionedFirst =
        new CompositeContract(new VersionedGetContract(), new SpringContract())
            .parseAndValidateMetadata(ModernApi.class)
            .getFirst();
    assertThat(versionedFirst.template().url()).isEqualTo("/v2/modern/{id}");
    // @PathVariable is only understood by SpringContract, so it is still honoured
    assertThat(versionedFirst.indexToName()).containsEntry(0, Collections.singletonList("id"));
  }
}
