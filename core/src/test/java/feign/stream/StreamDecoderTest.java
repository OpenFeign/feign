/**
 * Copyright 2012-2018 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Feign;
import feign.Request;
import feign.Request.HttpMethod;
import feign.RequestLine;
import feign.Response;
import feign.Util;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;
import static feign.Util.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class StreamDecoderTest {

  interface StreamInterface {
    @RequestLine("GET /")
    Stream<String> get();

    @RequestLine("GET /cars")
    Stream<Car> getCars();

    class Car {
      public String name;
      public String manufacturer;
    }
  }

  private String carsJson = ""//
      + "[\n"//
      + "  {\n"//
      + "    \"name\": \"Megane\",\n"//
      + "    \"manufacturer\": \"Renault\"\n"//
      + "  },\n"//
      + "  {\n"//
      + "    \"name\": \"C4\",\n"//
      + "    \"manufacturer\": \"Citroën\"\n"//
      + "  }\n"//
      + "]\n";

  @Test
  public void simpleStreamTest() {
    MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBody("foo\nbar"));

    StreamInterface api = Feign.builder()
        .decoder(StreamDecoder.create(
            (response, type) -> new BufferedReader(response.body().asReader()).lines().iterator()))
        .doNotCloseAfterDecode()
        .target(StreamInterface.class, server.url("/").toString());

    try (Stream<String> stream = api.get()) {
      assertThat(stream.collect(Collectors.toList())).isEqualTo(Arrays.asList("foo", "bar"));
    }
  }

  @Test
  public void shouldCloseIteratorWhenStreamClosed() throws IOException {
    Response response = Response.builder()
        .status(200)
        .reason("OK")
        .headers(Collections.emptyMap())
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .body("", UTF_8)
        .build();

    TestCloseableIterator it = new TestCloseableIterator();
    StreamDecoder decoder = new StreamDecoder((r, t) -> it);

    try (Stream<?> stream =
        (Stream) decoder.decode(response, new TypeReference<Stream<String>>() {}.getType())) {
      assertThat(stream.collect(Collectors.toList())).hasSize(1);
      assertThat(it.called).isTrue();
    } finally {
      assertThat(it.closed).isTrue();
    }
  }

  static class TestCloseableIterator implements Iterator<String>, Closeable {
    boolean called;
    boolean closed;

    @Override
    public void close() {
      this.closed = true;
    }

    @Override
    public boolean hasNext() {
      return !called;
    }

    @Override
    public String next() {
      called = true;
      return "feign";
    }
  }
}
