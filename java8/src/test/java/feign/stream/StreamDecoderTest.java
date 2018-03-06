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

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import feign.Feign;
import feign.RequestLine;
import feign.codec.DecodeException;
import feign.jackson.JacksonIteratorDecoder;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

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
  public void simpleStreamTest() throws IOException, InterruptedException {
    MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBody("foo\nbar"));

    StreamInterface api = Feign.builder()
        .decoder(StreamDecoder.create((response, type) -> new BufferedReader(response.body().asReader()).lines().iterator()))
        .doNotCloseAfterDecode()
        .target(StreamInterface.class, server.url("/").toString());

    try (Stream<String> stream = api.get()) {
      assertThat(stream.collect(Collectors.toList())).isEqualTo(Arrays.asList("foo", "bar"));
    }
  }

  @Test
  public void simpleJsonStreamTest() throws IOException, InterruptedException {
    MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBody(carsJson));

    ObjectMapper mapper = new ObjectMapper();

    StreamInterface api = Feign.builder()
        .decoder(StreamDecoder.create(JacksonIteratorDecoder.create()))
        .doNotCloseAfterDecode()
        .target(StreamInterface.class, server.url("/").toString());

    try (Stream<StreamInterface.Car> stream = api.getCars()) {
      assertThat(stream.collect(Collectors.toList())).hasSize(2);
    }
  }
}
