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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import feign.codec.Encoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RequestTemplateFactoryTest {
  interface Api {
    @RequestLine("POST /events/{topic}?cursor={cursor}")
    @Headers("X-Topic: {topic}")
    String events(@Param("topic") String topic, @Param("cursor") String cursor, String body);

    @RequestLine("GET /events")
    String mapped(@QueryMap Map<String, Object> query, @HeaderMap Map<String, Object> headers);
  }

  final Target<Api> target = new Target.HardCodedTarget<>(Api.class, "http://localhost");

  MethodMetadata metadata(String method) {
    return new DefaultContract()
        .parseAndValidateMetadata(Api.class).stream()
            .filter(md -> md.method().getName().equals(method))
            .findFirst()
            .get();
  }

  @Test
  void bindsLazilyWithoutExecutingAClient() {
    AtomicInteger encoded = new AtomicInteger();
    Encoder encoder =
        (body, type, template) -> {
          encoded.incrementAndGet();
          new Encoder.Default().encode(body, type, template);
        };
    RequestTemplateFactory factory =
        RequestTemplateFactory.forMethod(
            target, metadata("events"), encoder, new QueryMapEncoder.Default());
    assertThat(encoded.get()).isZero();
    RequestTemplate template = factory.create(new Object[] {"prices", "a b&c", "payload"});
    assertThat(encoded.get()).isEqualTo(1);
    // Interceptors and target are deliberately applied by the transport's method handler.
    template.header("Authorization", "test-value");
    Request request = target.apply(template);
    assertThat(request.url()).isEqualTo("http://localhost/events/prices?cursor=a%20b%26c");
    assertThat(request.headers()).containsEntry("X-Topic", Collections.singletonList("prices"));
    assertThat(request.headers())
        .containsEntry("Authorization", Collections.singletonList("test-value"));
    assertThat(new String(request.body(), Util.UTF_8)).isEqualTo("payload");
  }

  @Test
  void createsIndependentTemplatesOnEachInvocation() {
    RequestTemplateFactory factory =
        RequestTemplateFactory.forMethod(
            target, metadata("events"), new Encoder.Default(), new QueryMapEncoder.Default());
    RequestTemplate first = factory.create(new Object[] {"first", "one", "body-1"});
    RequestTemplate second = factory.create(new Object[] {"second", "two", "body-2"});
    first.header("X-Only-First", "yes");
    assertThat(target.apply(first).url()).contains("/first?cursor=one");
    assertThat(target.apply(second).url()).contains("/second?cursor=two");
    assertThat(second.headers()).doesNotContainKey("X-Only-First");
  }

  @Test
  void retainsNativeQueryAndHeaderMapSemantics() {
    RequestTemplateFactory factory =
        RequestTemplateFactory.forMethod(
            target, metadata("mapped"), new Encoder.Default(), new QueryMapEncoder.Default());
    RequestTemplate template =
        factory.create(
            new Object[] {
              Collections.singletonMap("tag", Arrays.asList("a b", "c&d")),
              Collections.singletonMap("X-Literal", "{not-a-template}")
            });
    Request request = target.apply(template);
    assertThat(request.url()).isEqualTo("http://localhost/events?tag=a%20b&tag=c%26d");
    assertThat(request.headers())
        .containsEntry("X-Literal", Collections.singletonList("{not-a-template}"));
  }

  @Test
  void rejectsMissingConfiguration() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                RequestTemplateFactory.forMethod(
                    null,
                    metadata("events"),
                    new Encoder.Default(),
                    new QueryMapEncoder.Default()));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                RequestTemplateFactory.forMethod(
                    target, null, new Encoder.Default(), new QueryMapEncoder.Default()));
  }
}
