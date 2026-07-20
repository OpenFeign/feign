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
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.Request.HttpMethod;
import feign.Response;
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
  void shouldDecodePrimitiveArrays() throws Exception {
    assertThat(iterator(Integer.class, "[0,1,2,3]")).toIterable().containsExactly(0, 1, 2, 3);
  }

  @Test
  void shouldNotSkipElementsOnHasNext() throws Exception {
    JacksonIterator<Integer> iterator = iterator(Integer.class, "[0]");
    assertThat(iterator).hasNext().hasNext();
    assertThat(iterator.next()).isZero();
    assertThat(iterator).isExhausted();
  }

  @Test
  void hasNextIsNotMandatory() throws Exception {
    JacksonIterator<Integer> iterator = iterator(Integer.class, "[0]");
    assertThat(iterator.next()).isZero();
    assertThat(iterator).isExhausted();
  }

  @Test
  void expectExceptionOnNoElements() throws Exception {
    JacksonIterator<Integer> iterator = iterator(Integer.class, "[0]");
    assertThat(iterator.next()).isZero();
    assertThatThrownBy(() -> iterator.next())
        .hasMessage(null)
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void shouldDecodeObjects() throws Exception {
    assertThat(iterator(User.class, "[{\"login\":\"bob\"},{\"login\":\"joe\"}]"))
        .toIterable()
        .containsExactly(new User("bob"), new User("joe"));
  }

  @Test
  void malformedObjectThrowsDecodeException() throws Exception {
    DecodeException exception =
        assertThatExceptionOfType(DecodeException.class)
            .isThrownBy(
                () ->
                    assertThat(iterator(User.class, "[{\"login\":\"bob\"},{\"login\":\"joe..."))
                        .toIterable()
                        .containsOnly(new User("bob")))
            .actual();
    assertThat(exception).hasCauseInstanceOf(IOException.class);
  }

  @Test
  void emptyBodyDecodesToEmptyIterator() throws Exception {
    assertThat(iterator(String.class, "")).toIterable().isEmpty();
  }

  @Test
  void unmodifiable() throws Exception {
    assertThatThrownBy(
            () -> {
              JacksonIterator<String> it = iterator(String.class, "[\"test\"]");

              assertThat(it).toIterable().containsExactly("test");
              it.remove();
            })
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void responseIsClosedAfterIteration() throws Exception {
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
            .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, null))
            .headers(Collections.emptyMap())
            .body(inputStream, jsonBytes.length)
            .build();

    assertThat(iterator(Boolean.class, response)).toIterable().hasSize(2);
    assertThat(closed.get()).isTrue();
  }

  @Test
  void responseIsClosedOnParseError() throws Exception {
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
            .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, null))
            .headers(Collections.emptyMap())
            .body(inputStream, jsonBytes.length)
            .build();

    assertThatThrownBy(() -> assertThat(iterator(Boolean.class, response)).toIterable().hasSize(1))
        .isInstanceOf(DecodeException.class);

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
            .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, null))
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
