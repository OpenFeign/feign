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

import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

class EncoderPredicateTest {

  private static RequestTemplate template(String contentType) {
    RequestTemplate template = new RequestTemplate();
    if (contentType != null) {
      template.header("Content-Type", contentType);
    }
    return template;
  }

  private static boolean test(EncoderPredicate predicate, String contentType) {
    return predicate.canEncode("body", String.class, template(contentType));
  }

  @Test
  void anyMatchesEverything() {
    EncoderPredicate any = EncoderPredicate.any();

    assertThat(test(any, "application/json")).isTrue();
    assertThat(test(any, null)).isTrue();
    assertThat(any.canEncode(null, null, template(null))).isTrue();
  }

  @Test
  void jsonContentTypeMatchesJsonOnly() {
    EncoderPredicate json = EncoderPredicate.jsonContentType();

    assertThat(test(json, "application/json")).isTrue();
    assertThat(test(json, "application/json;charset=utf-8")).isTrue();
    assertThat(test(json, "application/vnd.github+json")).isTrue();
    assertThat(test(json, "text/json")).isTrue();
    assertThat(test(json, "application/xml")).isFalse();
    assertThat(test(json, null)).isFalse();
  }

  @Test
  void xmlContentTypeMatchesXmlOnly() {
    EncoderPredicate xml = EncoderPredicate.xmlContentType();

    assertThat(test(xml, "application/xml")).isTrue();
    assertThat(test(xml, "text/xml")).isTrue();
    assertThat(test(xml, "application/soap+xml")).isTrue();
    assertThat(test(xml, "application/json")).isFalse();
    assertThat(test(xml, null)).isFalse();
  }

  @Test
  void contentTypeMatchesExactMediaTypeIgnoringParameters() {
    EncoderPredicate form = EncoderPredicate.contentType("application/x-www-form-urlencoded");

    assertThat(test(form, "application/x-www-form-urlencoded")).isTrue();
    assertThat(test(form, "APPLICATION/X-WWW-FORM-URLENCODED")).isTrue();
    assertThat(test(form, "application/x-www-form-urlencoded;charset=utf-8")).isTrue();
    assertThat(test(form, "application/x-www-form-urlencoded-extra")).isFalse();
    assertThat(test(form, "application/json")).isFalse();
  }

  @Test
  void headerNameIsMatchedCaseInsensitively() {
    RequestTemplate template = new RequestTemplate();
    template.header("content-type", "application/json");

    assertThat(EncoderPredicate.jsonContentType().canEncode("body", String.class, template))
        .isTrue();
  }

  @Test
  void emptyBodyMatchesNullBodyOnly() {
    EncoderPredicate empty = EncoderPredicate.emptyBody();

    assertThat(empty.canEncode(null, String.class, template(null))).isTrue();
    assertThat(empty.canEncode("body", String.class, template(null))).isFalse();
  }

  @Test
  void bodyTypeMatchesExactType() {
    EncoderPredicate bytes = EncoderPredicate.bodyType(byte[].class);

    assertThat(bytes.canEncode(new byte[0], byte[].class, template(null))).isTrue();
    assertThat(bytes.canEncode("body", String.class, template(null))).isFalse();
  }

  @Test
  void formEncodedMatchesTheFormBodyTypeMarker() {
    EncoderPredicate form = EncoderPredicate.formEncoded();

    assertThat(form.canEncode(null, Encoder.MAP_STRING_WILDCARD, template(null))).isTrue();
    assertThat(form.canEncode("body", String.class, template(null))).isFalse();
  }

  @Test
  void predicatesDescribeThemselves() {
    assertThat(EncoderPredicate.any()).hasToString("any request");
    assertThat(EncoderPredicate.jsonContentType()).hasToString("Content-Type is JSON");
    assertThat(EncoderPredicate.xmlContentType()).hasToString("Content-Type is XML");
    assertThat(EncoderPredicate.contentType("text/plain"))
        .hasToString("Content-Type is text/plain");
    assertThat(EncoderPredicate.emptyBody()).hasToString("body is empty");
    assertThat(EncoderPredicate.bodyType(byte[].class)).hasToString("body type is byte[]");
    assertThat(EncoderPredicate.formEncoded()).hasToString("body is form encoded");
    assertThat(EncoderPredicate.describedAs("it is Tuesday", (o, b, t) -> true))
        .hasToString("it is Tuesday");
  }

  @Test
  void combinedPredicatesDescribeThemselves() {
    EncoderPredicate json = EncoderPredicate.jsonContentType();
    EncoderPredicate xml = EncoderPredicate.xmlContentType();

    assertThat(json.or(xml)).hasToString("(Content-Type is JSON or Content-Type is XML)");
    assertThat(json.and(xml)).hasToString("(Content-Type is JSON and Content-Type is XML)");
    assertThat(json.negate()).hasToString("not (Content-Type is JSON)");
  }

  @Test
  void combinators() {
    EncoderPredicate json = EncoderPredicate.jsonContentType();
    EncoderPredicate xml = EncoderPredicate.xmlContentType();

    assertThat(test(json.or(xml), "application/xml")).isTrue();
    assertThat(test(json.or(xml), "text/plain")).isFalse();
    assertThat(test(json.and(xml), "application/json")).isFalse();
    assertThat(test(json.negate(), "application/xml")).isTrue();
    assertThat(test(json.negate(), "application/json")).isFalse();
  }
}
