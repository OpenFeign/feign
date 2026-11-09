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

import feign.Request.HttpMethod;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class ResponseTest {

  @Test
  void reasonPhraseIsOptional() {
    Response response =
        Response.builder()
            .status(200)
            .headers(Collections.<String, Collection<String>>emptyMap())
            .request(
                Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
            .body(new byte[0])
            .build();

    assertThat(response.reason()).isNull();
    assertThat(response.toString()).startsWith("HTTP/1.1 200");
  }

  @Test
  void canAccessHeadersCaseInsensitively() {
    Map<String, Collection<String>> headersMap = new LinkedHashMap<>();
    List<String> valueList = Collections.singletonList("application/json");
    headersMap.put("Content-Type", valueList);
    Response response =
        Response.builder()
            .status(200)
            .headers(headersMap)
            .request(
                Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
            .body(new byte[0])
            .build();
    assertThat(response.headers())
        .hasEntrySatisfying(
            "content-type",
            value -> {
              assertThat(value).contains("application/json");
            })
        .hasEntrySatisfying(
            "Content-Type",
            value -> {
              assertThat(value).contains("application/json");
            });
  }

  @Test
  void charsetSupportsMediaTypesWithQuotedCharset() {
    Map<String, Collection<String>> headersMap = new LinkedHashMap<>();
    List<String> valueList = Collections.singletonList("application/json; charset=\"utf-8\"");
    headersMap.put("Content-Type", valueList);
    Response response =
        Response.builder()
            .status(200)
            .headers(headersMap)
            .request(
                Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
            .body(new byte[0])
            .build();
    assertThat(response.charset()).isEqualTo(Util.UTF_8);
  }

  @Test
  void charsetDefaultsToUtf8ForIllegalCharsetName() {
    Map<String, Collection<String>> headersMap = new LinkedHashMap<>();
    headersMap.put("Content-Type", Collections.singletonList("text/plain; charset=@"));
    Response response =
        Response.builder()
            .status(200)
            .headers(headersMap)
            .request(
                Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
            .body(new byte[0])
            .build();
    assertThat(response.charset()).isEqualTo(Util.UTF_8);
  }

  @Test
  void charsetDefaultsToUtf8ForUnsupportedCharset() {
    Map<String, Collection<String>> headersMap = new LinkedHashMap<>();
    headersMap.put("Content-Type", Collections.singletonList("text/plain; charset=made-up-99"));
    Response response =
        Response.builder()
            .status(200)
            .headers(headersMap)
            .request(
                Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
            .body(new byte[0])
            .build();
    assertThat(response.charset()).isEqualTo(Util.UTF_8);
  }

  @Test
  void charsetSupportsCharsetAsSecondParameter() {
    // A charset may follow other media type parameters (RFC 9110 8.3.1).
    Response response = responseWithContentType("text/plain;format=flowed;charset=ISO-8859-1");
    assertThat(response.charset()).isEqualTo(StandardCharsets.ISO_8859_1);
  }

  @Test
  void charsetSupportsCharsetAsLaterParameter() {
    Response response =
        responseWithContentType("text/plain; format=flowed; boundary=xyz; charset=ISO-8859-1");
    assertThat(response.charset()).isEqualTo(StandardCharsets.ISO_8859_1);
  }

  @Test
  void charsetParameterNameIsCaseInsensitiveWhenNotFirstParameter() {
    Response response = responseWithContentType("text/plain; Format=Flowed; Charset=ISO-8859-1");
    assertThat(response.charset()).isEqualTo(StandardCharsets.ISO_8859_1);
  }

  @Test
  void charsetSupportsQuotedCharsetValueWhenNotFirstParameter() {
    Response response = responseWithContentType("text/plain;format=flowed;charset=\"ISO-8859-1\"");
    assertThat(response.charset()).isEqualTo(StandardCharsets.ISO_8859_1);
  }

  @Test
  void quotedParameterValueContainingCharsetTextDoesNotAffectCharset() {
    // RFC 9110 5.6.6 allows a quoted-string parameter value to contain ";", so the fake
    // "charset=UTF-16" below is data and must not be read as the charset parameter
    Response response =
        responseWithContentType("text/plain; note=\"x;charset=UTF-16\"; charset=ISO-8859-1");
    assertThat(response.charset()).isEqualTo(StandardCharsets.ISO_8859_1);
  }

  @Test
  void escapedQuoteInQuotedParameterValueDoesNotHideCharsetParameters() {
    // the \" below is escaped, so the quoted value runs to the next unescaped quote and keeps the
    // ";charset=UTF-16" text as data
    Response response =
        responseWithContentType("text/plain; note=\"a\\\";charset=UTF-16\"; charset=ISO-8859-1");
    assertThat(response.charset()).isEqualTo(StandardCharsets.ISO_8859_1);
  }

  @Test
  void charsetDefaultsToUtf8WhenNoCharsetParameterIsPresent() {
    Response response = responseWithContentType("text/plain; format=flowed");
    assertThat(response.charset()).isEqualTo(Util.UTF_8);
  }

  @Test
  void charsetDefaultsToUtf8ForUnsupportedCharsetInLaterParameter() {
    Response response = responseWithContentType("text/plain; format=flowed; charset=made-up-99");
    assertThat(response.charset()).isEqualTo(Util.UTF_8);
  }

  @Test
  void headerValuesWithSameNameOnlyVaryingInCaseAreMerged() {
    Map<String, Collection<String>> headersMap = new LinkedHashMap<>();
    headersMap.put("Set-Cookie", Arrays.asList("Cookie-A=Value", "Cookie-B=Value"));
    headersMap.put("set-cookie", Collections.singletonList("Cookie-C=Value"));

    Response response =
        Response.builder()
            .status(200)
            .headers(headersMap)
            .request(
                Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
            .body(new byte[0])
            .build();

    assertThat(response.headers())
        .hasEntrySatisfying(
            "set-cookie",
            value -> {
              assertThat(value).contains("Cookie-A=Value", "Cookie-B=Value", "Cookie-C=Value");
            });
  }

  @Test
  void headersAreOptional() {
    Response response =
        Response.builder()
            .status(200)
            .request(
                Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
            .body(new byte[0])
            .build();
    assertThat(response.headers()).isNotNull().isEmpty();
  }

  @Test
  void support1xxStatusCodes() {
    Response response =
        Response.builder()
            .status(103)
            .request(
                Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
            .body((Response.Body) null)
            .build();

    assertThat(response.status()).isEqualTo(103);
  }

  @Test
  void statusCodesOfAnyValueAreAllowed() {
    Lists.list(600, 50, 35600)
        .forEach(
            statusCode -> {
              Response response =
                  Response.builder()
                      .status(statusCode)
                      .request(
                          Request.create(
                              HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
                      .body((Response.Body) null)
                      .build();

              assertThat(response.status()).isEqualTo(statusCode);
            });
  }

  @Test
  void protocolVersionDefaultsToHttp1_1() {
    Response response =
        Response.builder()
            .status(200)
            .request(
                Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
            .protocolVersion(null)
            .body(new byte[0])
            .build();

    assertThat(response.protocolVersion()).isEqualTo(Request.ProtocolVersion.HTTP_1_1);
    assertThat(response.toString()).startsWith("HTTP/1.1 200");
  }

  private Response responseWithContentType(String contentType) {
    Map<String, Collection<String>> headersMap = new LinkedHashMap<>();
    headersMap.put("Content-Type", Collections.singletonList(contentType));
    return Response.builder()
        .status(200)
        .headers(headersMap)
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .body(new byte[0])
        .build();
  }
}
