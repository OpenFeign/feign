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
package feign;

import static feign.assertj.FeignAssertions.assertThat;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.MapEntry.entry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CompositeContractTest {

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  @interface AlphaPath {
    String value();
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  @interface AlphaGet {
    String value();
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.PARAMETER)
  @interface AlphaQuery {
    String value();
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  @interface BetaAccept {
    String value();
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  @interface BetaPost {
    String value();
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.PARAMETER)
  @interface BetaHeader {
    String value();
  }

  /** Understood by both {@link AlphaContract} and {@link BetaContract}, but differently. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  @interface SharedOwner {}

  /** Understood by both {@link AlphaContract} and {@link BetaContract}, but differently. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  @interface SharedCall {
    String value();
  }

  /** Understood by both {@link AlphaContract} and {@link BetaContract}, but differently. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.PARAMETER)
  @interface SharedParam {
    String value();
  }

  static class AlphaContract extends DeclarativeContract {
    AlphaContract() {
      registerClassAnnotation(AlphaPath.class, (path, data) -> data.template().uri(path.value()));
      registerMethodAnnotation(
          AlphaGet.class,
          (get, data) -> {
            data.template().method(Request.HttpMethod.GET);
            data.template().uri(get.value(), true);
          });
      registerParameterAnnotation(
          AlphaQuery.class,
          (query, data, index) -> {
            data.template().query(query.value(), "{" + query.value() + "}");
            nameParam(data, query.value(), index);
          });

      registerClassAnnotation(
          SharedOwner.class, (owner, data) -> data.template().header("X-Owner", "alpha"));
      registerMethodAnnotation(
          SharedCall.class,
          (call, data) -> {
            data.template().method(Request.HttpMethod.GET);
            data.template().uri("/alpha" + call.value(), true);
          });
      registerParameterAnnotation(
          SharedParam.class,
          (param, data, index) -> {
            data.template().query(param.value(), "{" + param.value() + "}");
            nameParam(data, param.value(), index);
          });
    }
  }

  static class BetaContract extends DeclarativeContract {
    BetaContract() {
      registerClassAnnotation(
          BetaAccept.class, (accept, data) -> data.template().header("Accept", accept.value()));
      registerMethodAnnotation(
          BetaPost.class,
          (post, data) -> {
            data.template().method(Request.HttpMethod.POST);
            data.template().uri(post.value(), true);
          });
      registerParameterAnnotation(
          BetaHeader.class,
          (header, data, index) -> {
            data.template().header(header.value(), "{" + header.value() + "}");
            nameParam(data, header.value(), index);
          });

      registerClassAnnotation(
          SharedOwner.class, (owner, data) -> data.template().header("X-Owner", "beta"));
      registerMethodAnnotation(
          SharedCall.class,
          (call, data) -> {
            data.template().method(Request.HttpMethod.POST);
            data.template().uri("/beta" + call.value(), true);
          });
      registerParameterAnnotation(
          SharedParam.class,
          (param, data, index) -> {
            data.template().header(param.value(), "{" + param.value() + "}");
            nameParam(data, param.value(), index);
          });
    }
  }

  @AlphaPath("/api")
  @BetaAccept("application/json")
  interface MixedApi {

    @AlphaGet("/items")
    String list(@AlphaQuery("q") String q, @BetaHeader("X-Trace") String trace);

    @BetaPost("/items")
    void create(String body);
  }

  @Test
  void mixesAnnotationsOfEveryDelegate() {
    Contract contract = new CompositeContract(new AlphaContract(), new BetaContract());

    Map<String, MethodMetadata> byKey =
        byConfigKey(contract.parseAndValidateMetadata(MixedApi.class));

    MethodMetadata list = byKey.get("MixedApi#list(String,String)");
    assertThat(list.template())
        .hasMethod("GET")
        .hasUrl("/api/items?q={q}")
        .hasQueries(entry("q", singletonList("{q}")))
        .hasHeaders(
            entry("Accept", singletonList("application/json")),
            entry("X-Trace", singletonList("{X-Trace}")));
    assertThat(list.indexToName())
        .containsExactly(entry(0, singletonList("q")), entry(1, singletonList("X-Trace")));
    assertThat(list.bodyIndex()).isNull();

    MethodMetadata create = byKey.get("MixedApi#create(String)");
    assertThat(create.template())
        .hasMethod("POST")
        .hasUrl("/api/items")
        .hasHeaders(entry("Accept", singletonList("application/json")));
    assertThat(create.bodyIndex()).isEqualTo(0);
  }

  @Test
  void eachDelegateAloneRejectsMixedApi() {
    assertThatThrownBy(() -> new AlphaContract().parseAndValidateMetadata(MixedApi.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MixedApi#create(String)")
        .hasMessageContaining("not annotated with HTTP method type");
    assertThatThrownBy(() -> new BetaContract().parseAndValidateMetadata(MixedApi.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MixedApi#list(String,String)")
        .hasMessageContaining("not annotated with HTTP method type");
  }

  @SharedOwner
  interface SharedApi {

    @SharedCall("/things")
    String find(@SharedParam("id") String id);
  }

  @Test
  void firstDelegateWinsWhenSeveralUnderstandTheSameAnnotation() {
    MethodMetadata alphaFirst =
        new CompositeContract(new AlphaContract(), new BetaContract())
            .parseAndValidateMetadata(SharedApi.class)
            .getFirst();

    assertThat(alphaFirst.template())
        .hasMethod("GET")
        .hasUrl("/alpha/things?id={id}")
        .hasQueries(entry("id", singletonList("{id}")))
        .hasHeaders(entry("X-Owner", singletonList("alpha")))
        .hasNoHeader("id");
    assertThat(alphaFirst.indexToName()).containsExactly(entry(0, singletonList("id")));

    MethodMetadata betaFirst =
        new CompositeContract(new BetaContract(), new AlphaContract())
            .parseAndValidateMetadata(SharedApi.class)
            .getFirst();

    assertThat(betaFirst.template())
        .hasMethod("POST")
        .hasUrl("/beta/things")
        .hasHeaders(entry("X-Owner", singletonList("beta")), entry("id", singletonList("{id}")));
    assertThat(betaFirst.template().queries()).isEmpty();
    assertThat(betaFirst.indexToName()).containsExactly(entry(0, singletonList("id")));
  }

  @Test
  void laterDelegateStillHandlesWhatTheFirstDoesNotUnderstand() {
    // BetaAccept is only known to BetaContract, so AlphaContract going first must not hide it.
    @SharedOwner
    @BetaAccept("text/plain")
    interface Api {
      @SharedCall("/x")
      String get();
    }

    MethodMetadata md =
        new CompositeContract(new AlphaContract(), new BetaContract())
            .parseAndValidateMetadata(Api.class)
            .getFirst();

    assertThat(md.template())
        .hasHeaders(
            entry("X-Owner", singletonList("alpha")), entry("Accept", singletonList("text/plain")));
  }

  @Headers({"Accept: application/json", "X-Api: v1"})
  interface DefaultStyleApi {

    @RequestLine("GET /repos/{owner}/{repo}/contributors?page={page}")
    List<String> contributors(
        @Param("owner") String owner, @Param("repo") String repo, @Param("page") int page);

    @RequestLine("POST /repos/{owner}/{repo}/issues")
    @Headers("Content-Type: application/json")
    @Body("%7B\"title\": \"{title}\"%7D")
    void createIssue(
        @Param("owner") String owner, @Param("repo") String repo, @Param("title") String title);

    @RequestLine("GET /search")
    List<String> search(@QueryMap Map<String, Object> query, @HeaderMap Map<String, Object> h);

    @RequestLine("PUT /upload")
    void upload(java.net.URI host, byte[] body, Request.Options options);

    @RequestLine(value = "GET /raw/{path}", decodeSlash = false)
    String raw(@Param(value = "path", encoded = true) String path);
  }

  @Test
  void singleDelegateBehavesLikeTheDelegate() {
    List<MethodMetadata> expected =
        new DefaultContract().parseAndValidateMetadata(DefaultStyleApi.class);
    List<MethodMetadata> actual =
        new CompositeContract(new DefaultContract())
            .parseAndValidateMetadata(DefaultStyleApi.class);

    assertThat(actual).hasSameSizeAs(expected);
    for (int i = 0; i < expected.size(); i++) {
      assertSameMetadata(actual.get(i), expected.get(i));
    }
  }

  interface InvalidDefaultStyleApi {
    @Headers("X-Foo: bar")
    String noRequestLine();
  }

  @Test
  void singleDelegateFailsLikeTheDelegate() {
    assertThatThrownBy(
            () ->
                new CompositeContract(new DefaultContract())
                    .parseAndValidateMetadata(InvalidDefaultStyleApi.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(
            "Method InvalidDefaultStyleApi#noRequestLine() not annotated with HTTP method type");
  }

  @Test
  void requiresAtLeastOneDelegate() {
    assertThatThrownBy(() -> new CompositeContract())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one");
    assertThatThrownBy(() -> new CompositeContract(Collections.emptyList()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one");
  }

  @Test
  void rejectsNullDelegates() {
    assertThatThrownBy(() -> new CompositeContract(new DefaultContract(), null))
        .isInstanceOf(NullPointerException.class);
  }

  static class AlwaysEncodeBody extends AlwaysEncodeBodyContract {}

  @Test
  void rejectsAlwaysEncodeBodyDelegates() {
    // the composite cannot honour AlwaysEncodeBodyContract on behalf of a delegate, so it refuses
    // instead of silently changing how that delegate's clients encode bodies.
    assertThatThrownBy(() -> new CompositeContract(new AlwaysEncodeBody()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("AlwaysEncodeBodyContract");
  }

  private static Map<String, MethodMetadata> byConfigKey(List<MethodMetadata> metadata) {
    return metadata.stream()
        .collect(Collectors.toMap(MethodMetadata::configKey, Function.identity()));
  }

  private static void assertSameMetadata(MethodMetadata actual, MethodMetadata expected) {
    assertThat(actual.configKey()).isEqualTo(expected.configKey());
    assertThat(actual.method()).isEqualTo(expected.method());
    assertThat(actual.returnType()).isEqualTo(expected.returnType());
    assertThat(actual.template().method()).isEqualTo(expected.template().method());
    assertThat(actual.template().url()).isEqualTo(expected.template().url());
    assertThat(actual.template().headers()).isEqualTo(expected.template().headers());
    assertThat(actual.template().queries()).isEqualTo(expected.template().queries());
    assertThat(actual.template().bodyTemplate()).isEqualTo(expected.template().bodyTemplate());
    assertThat(actual.template().decodeSlash()).isEqualTo(expected.template().decodeSlash());
    assertThat(actual.indexToName()).isEqualTo(expected.indexToName());
    assertThat(actual.indexToEncoded()).isEqualTo(expected.indexToEncoded());
    assertThat(actual.indexToExpanderClass()).isEqualTo(expected.indexToExpanderClass());
    assertThat(actual.formParams()).isEqualTo(expected.formParams());
    assertThat(actual.bodyIndex()).isEqualTo(expected.bodyIndex());
    assertThat(actual.bodyType()).isEqualTo(expected.bodyType());
    assertThat(actual.urlIndex()).isEqualTo(expected.urlIndex());
    assertThat(actual.queryMapIndex()).isEqualTo(expected.queryMapIndex());
    assertThat(actual.headerMapIndex()).isEqualTo(expected.headerMapIndex());
    assertThat(actual.alwaysEncodeBody()).isEqualTo(expected.alwaysEncodeBody());
    assertThat(actual.parameterToIgnore()).isEqualTo(expected.parameterToIgnore());
    assertThat(actual.isIgnored()).isEqualTo(expected.isIgnored());
  }
}
