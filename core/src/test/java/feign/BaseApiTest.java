/*
 * Copyright 2015 Netflix, Inc.
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

import com.google.gson.reflect.TypeToken;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import org.junit.Rule;
import org.junit.Test;

import java.lang.reflect.Type;
import java.util.List;

import feign.codec.Decoder;
import feign.codec.Encoder;

import static feign.assertj.MockWebServerAssertions.assertThat;

public class BaseApiTest {

  @Rule
  public final MockWebServer server = new MockWebServer();

  interface BaseApi<K, M> {

    @RequestLine("GET /api/{key}")
    Entity<K, M> get(@Param("key") K key);

    @RequestLine("POST /api")
    Entities<K, M> getAll(Keys<K> keys);
  }

  static class Keys<K> {

    List<K> keys;
  }

  static class Entity<K, M> {

    K key;
    M model;
  }

  static class Entities<K, M> {

    List<Entity<K, M>> entities;
  }

  interface MyApi extends BaseApi<String, Long> {

  }

  @Test
  public void resolvesParameterizedResult() throws InterruptedException {
    server.enqueue(new MockResponse().setBody("foo"));

    String baseUrl = server.url("/default").toString();

    Feign.builder()
        .decoder(new Decoder() {
          @Override
          public Object decode(Response response, Type type) {
            assertThat(type)
                .isEqualTo(new TypeToken<Entity<String, Long>>() {
                }.getType());
            return null;
          }
        })
        .target(MyApi.class, baseUrl).get("foo");

    assertThat(server.takeRequest()).hasPath("/default/api/foo");
  }

  @Test
  public void resolvesBodyParameter() throws InterruptedException {
    server.enqueue(new MockResponse().setBody("foo"));

    String baseUrl = server.url("/default").toString();

    Feign.builder()
        .encoder(new Encoder() {
          @Override
          public void encode(Object object, Type bodyType, RequestTemplate template) {
            assertThat(bodyType)
                .isEqualTo(new TypeToken<Keys<String>>() {
                }.getType());
          }
        })
        .decoder(new Decoder() {
          @Override
          public Object decode(Response response, Type type) {
            assertThat(type)
                .isEqualTo(new TypeToken<Entities<String, Long>>() {
                }.getType());
            return null;
          }
        })
        .target(MyApi.class, baseUrl).getAll(new Keys<String>());
  }
}
