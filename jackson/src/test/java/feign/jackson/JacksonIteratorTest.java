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
package feign.jackson;

import static feign.Util.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.Request.HttpMethod;
import feign.Response;
import feign.Util;
import feign.codec.DecodeException;
import feign.jackson.JacksonIteratorDecoder.JacksonIterator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class JacksonIteratorTest {

  @Test
  void shouldDecodePrimitiveArrays() throws IOException {
    assertThat(iterator(Integer.class, "[0,1,2,3]")).toIterable().containsExactly(0, 1, 2, 3);
  }

  @Test
  void shouldNotSkipElementsOnHasNext() throws IOException {
    JacksonIterator<Integer> iterator = iterator(Integer.class, "[0]");
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.next()).isEqualTo(0);
    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  void hasNextIsNotMandatory() throws IOException {
    JacksonIterator<Integer> iterator = iterator(Integer.class, "[0]");
    assertThat(iterator.next()).isEqualTo(0);
    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  void expectExceptionOnNoElements() throws IOException {
    JacksonIterator<Integer> iterator = iterator(Integer.class, "[0]");
    assertThat(iterator.next()).isEqualTo(0);
    assertThatThrownBy(() -> iterator.next())
        .hasMessage(null)
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void shouldDecodeObjects() throws IOException {
    assertThat(iterator(User.class, "[{\"login\":\"bob\"},{\"login\":\"joe\"}]"))
        .toIterable()
        .containsExactly(new User("bob"), new User("joe"));
  }

  @Test
  void malformedObjectThrowsDecodeException() throws IOException {
    DecodeException exception =
        assertThrows(
            DecodeException.class,
            () ->
                assertThat(iterator(User.class, "[{\"login\":\"bob\"},{\"login\":\"joe..."))
                    .toIterable()
                    .containsOnly(new User("bob")));
    assertThat(exception).hasCauseInstanceOf(IOException.class);
  }

  @Test
  void emptyBodyDecodesToEmptyIterator() throws IOException {
    assertThat(iterator(String.class, "")).toIterable().isEmpty();
  }

  @Test
  void unmodifiable() throws IOException {
    assertThatExceptionOfType(UnsupportedOperationException.class)
        .isThrownBy(
            () -> {
              JacksonIterator<String> it = iterator(String.class, "[\"test\"]");

              assertThat(it).toIterable().containsExactly("test");
              it.remove();
            });
  }

  @Test
  void responseIsClosedAfterIteration() throws IOException {
    final AtomicBoolean closed = new AtomicBoolean();

    byte[] jsonBytes = "[false, true]".getBytes(UTF_8);
    InputStream inputStream =
        new ByteArrayInputStream(jsonBytes) {
          @Override
          public void close() throws IOException {
            closed.set(true);
            super.close();
          }
        };
    Response response =
        Response.builder()
            .status(200)
            .reason("OK")
            .request(
                Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
            .headers(Collections.emptyMap())
            .body(inputStream, jsonBytes.length)
            .build();

    assertThat(iterator(Boolean.class, response)).toIterable().hasSize(2);
    assertThat(closed.get()).isTrue();
  }

  @Test
  void responseIsClosedOnParseError() throws IOException {
    final AtomicBoolean closed = new AtomicBoolean();

    byte[] jsonBytes = "[error".getBytes(UTF_8);
    InputStream inputStream =
        new ByteArrayInputStream(jsonBytes) {
          @Override
          public void close() throws IOException {
            closed.set(true);
            super.close();
          }
        };
    Response response =
        Response.builder()
            .status(200)
            .reason("OK")
            .request(
                Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
            .headers(Collections.emptyMap())
            .body(inputStream, jsonBytes.length)
            .build();

    assertThrows(
        DecodeException.class,
        () -> assertThat(iterator(Boolean.class, response)).toIterable().hasSize(1));

    assertThat(closed.get()).isTrue();
  }

  static class User extends LinkedHashMap<String, Object> {

    private static final long serialVersionUID = 1L;

    User() {
      // for reflective instantiation.
    }

    User(String login) {
      put("login", login);
    }
  }

  <T> JacksonIterator<T> iterator(Class<T> type, String json) throws IOException {
    Response response =
        Response.builder()
            .status(200)
            .reason("OK")
            .request(
                Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
            .headers(Collections.emptyMap())
            .body(json, UTF_8)
            .build();
    return iterator(type, response);
  }

  <T> JacksonIterator<T> iterator(Class<T> type, Response response) throws IOException {
    return new JacksonIterator<>(
        type.getGenericSuperclass(), new ObjectMapper(), response, response.body().asReader());
  }
}
