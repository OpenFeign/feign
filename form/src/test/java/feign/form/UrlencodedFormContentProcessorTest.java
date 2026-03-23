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

import feign.CollectionFormat;
import feign.Feign;
import feign.Headers;
import feign.RequestLine;
import feign.form.utils.UndertowServer;
import feign.jackson.JacksonEncoder;
import io.undertow.server.HttpServerExchange;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

class UrlencodedFormContentProcessorTest {

  @Test
  void arrayValueUsesDefaultExplodedCollectionFormat() {
    assertEncodedBody(
        "from=%2B987654321&to=%2B123456789&tags=one&tags=two",
        new String[] {"one", "two"}, Client::map);
  }

  @Test
  void collectionValueUsesDefaultExplodedCollectionFormat() {
    assertEncodedBody(
        "from=%2B987654321&to=%2B123456789&tags=one&tags=two",
        Arrays.asList("one", "two"), Client::map);
  }

  @Test
  void arrayValueUsesCsvCollectionFormat() {
    assertEncodedBody(
        "from=%2B987654321&to=%2B123456789&tags=one%2Ctwo",
        new String[] {"one", "two"}, Client::mapCsv);
  }

  @Test
  void collectionValueUsesCsvCollectionFormat() {
    assertEncodedBody(
        "from=%2B987654321&to=%2B123456789&tags=one%2Ctwo",
        Arrays.asList("one", "two"), Client::mapCsv);
  }

  @Test
  void arrayValueUsesSsvCollectionFormat() {
    assertEncodedBody(
        "from=%2B987654321&to=%2B123456789&tags=one%20two",
        new String[] {"one", "two"}, Client::mapSsv);
  }

  @Test
  void collectionValueUsesSsvCollectionFormat() {
    assertEncodedBody(
        "from=%2B987654321&to=%2B123456789&tags=one%20two",
        Arrays.asList("one", "two"), Client::mapSsv);
  }

  @Test
  void arrayValueUsesTsvCollectionFormat() {
    assertEncodedBody(
        "from=%2B987654321&to=%2B123456789&tags=one%09two",
        new String[] {"one", "two"}, Client::mapTsv);
  }

  @Test
  void collectionValueUsesTsvCollectionFormat() {
    assertEncodedBody(
        "from=%2B987654321&to=%2B123456789&tags=one%09two",
        Arrays.asList("one", "two"), Client::mapTsv);
  }

  @Test
  void arrayValueUsesPipesCollectionFormat() {
    assertEncodedBody(
        "from=%2B987654321&to=%2B123456789&tags=one%7Ctwo",
        new String[] {"one", "two"}, Client::mapPipes);
  }

  @Test
  void collectionValueUsesPipesCollectionFormat() {
    assertEncodedBody(
        "from=%2B987654321&to=%2B123456789&tags=one%7Ctwo",
        Arrays.asList("one", "two"), Client::mapPipes);
  }

  private void assertEncodedBody(
      String expectedBody,
      Object tags,
      BiFunction<Client, Map<String, Object>, String> requestCall) {
    try (var server =
        UndertowServer.builder()
            .callback((exchange, message) -> assertRequest(exchange, message, expectedBody))
            .start()) {
      var client =
          Feign.builder()
              .encoder(new FormEncoder(new JacksonEncoder()))
              .target(Client.class, server.getConnectUrl());

      var data = createRequestData(tags);
      assertThat(requestCall.apply(client, data)).isEqualTo("ok");
    }
  }

  private Map<String, Object> createRequestData(Object tags) {
    var data = new LinkedHashMap<String, Object>();
    data.put("from", "+987654321");
    data.put("to", "+123456789");
    data.put("tags", tags);
    return data;
  }

  private void assertRequest(HttpServerExchange exchange, byte[] message, String expectedBody) {
    assertThat(exchange.getRequestHeaders().getFirst(io.undertow.util.Headers.CONTENT_TYPE))
        .isEqualTo("application/x-www-form-urlencoded; charset=UTF-8");
    assertThat(message).asString().isEqualTo(expectedBody);

    exchange.getResponseHeaders().put(io.undertow.util.Headers.CONTENT_TYPE, "text/plain");
    exchange.getResponseSender().send("ok");
  }

  interface Client {

    @RequestLine("POST")
    @Headers("Content-Type: application/x-www-form-urlencoded; charset=utf-8")
    String map(Map<String, Object> data);

    @RequestLine(value = "POST", collectionFormat = CollectionFormat.CSV)
    @Headers("Content-Type: application/x-www-form-urlencoded; charset=utf-8")
    String mapCsv(Map<String, Object> data);

    @RequestLine(value = "POST", collectionFormat = CollectionFormat.SSV)
    @Headers("Content-Type: application/x-www-form-urlencoded; charset=utf-8")
    String mapSsv(Map<String, Object> data);

    @RequestLine(value = "POST", collectionFormat = CollectionFormat.TSV)
    @Headers("Content-Type: application/x-www-form-urlencoded; charset=utf-8")
    String mapTsv(Map<String, Object> data);

    @RequestLine(value = "POST", collectionFormat = CollectionFormat.PIPES)
    @Headers("Content-Type: application/x-www-form-urlencoded; charset=utf-8")
    String mapPipes(Map<String, Object> data);
  }
}
