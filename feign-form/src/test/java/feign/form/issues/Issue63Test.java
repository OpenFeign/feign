/*
 * Copyright 2024 the original author or authors.
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

package feign.form.issues;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import io.undertow.server.HttpServerExchange;
import lombok.val;
import org.junit.jupiter.api.Test;

import feign.Feign;
import feign.Headers;
import feign.RequestLine;
import feign.form.FormEncoder;
import feign.form.utils.UndertowServer;
import feign.jackson.JacksonEncoder;

// https://github.com/OpenFeign/feign-form/issues/63
class Issue63Test {

  @Test
  void test () {
    try (val server = UndertowServer.builder().callback(this::handleRequest).start()) {
      val client = Feign.builder()
        .encoder(new FormEncoder(new JacksonEncoder()))
        .target(Client.class, server.getConnectUrl());

      val data = new HashMap<String, String>();
      data.put("from", "+987654321");
      data.put("to", "+123456789");
      data.put("body", "hello world");

      assertThat(client.map(data))
        .isEqualTo("ok");
    }
  }

  private void handleRequest (HttpServerExchange exchange, byte[] message) {
    // assert request
    assertThat(exchange.getRequestHeaders().getFirst(io.undertow.util.Headers.CONTENT_TYPE))
      .isEqualTo("application/x-www-form-urlencoded; charset=UTF-8");
    assertThat(message)
      .asString()
      .isEqualTo("from=%2B987654321&to=%2B123456789&body=hello+world");

    // build response
    exchange.getResponseHeaders()
      .put(io.undertow.util.Headers.CONTENT_TYPE, "text/plain");
    exchange.getResponseSender()
      .send("ok");
  }

  interface Client {

    @RequestLine("POST")
    @Headers("Content-Type: application/x-www-form-urlencoded; charset=utf-8")
    String map (Map<String, String> data);
  }
}
