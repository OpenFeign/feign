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
package feign.benchmark;

import com.fasterxml.jackson.core.type.TypeReference;
import feign.Request;
import feign.Request.HttpMethod;
import feign.Response;
import feign.Util;
import feign.codec.Decoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonIteratorDecoder;
import feign.stream.StreamDecoder;
import org.openjdk.jmh.annotations.*;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * This test shows up how fast different json array response processing implementations are.
 */
@State(Scope.Thread)
public class DecoderIteratorsBenchmark {

  @Param({"list", "iterator", "stream"})
  private String api;

  @Param({"10", "100"})
  private String size;

  private Response response;

  private Decoder decoder;
  private Type type;

  @Benchmark
  @Warmup(iterations = 5, time = 1)
  @Measurement(iterations = 10, time = 1)
  @Fork(3)
  @BenchmarkMode(Mode.AverageTime)
  @OutputTimeUnit(TimeUnit.NANOSECONDS)
  public void decode() throws Exception {
    fetch(decoder.decode(response, type));
  }

  @SuppressWarnings("unchecked")
  private void fetch(Object o) {
    Iterator<Car> cars;

    if (o instanceof Collection) {
      cars = ((Collection<Car>) o).iterator();
    } else if (o instanceof Stream) {
      cars = ((Stream<Car>) o).iterator();
    } else {
      cars = (Iterator<Car>) o;
    }

    while (cars.hasNext()) {
      cars.next();
    }
  }

  @Setup(Level.Invocation)
  public void buildResponse() {
    response = Response.builder()
        .status(200)
        .reason("OK")
        .request(Request.create(HttpMethod.GET, "/", Collections.emptyMap(), null, Util.UTF_8))
        .headers(Collections.emptyMap())
        .body(carsJson(Integer.valueOf(size)), Util.UTF_8)
        .build();
  }

  @Setup(Level.Trial)
  public void buildDecoder() {
    switch (api) {
      case "list":
        decoder = new JacksonDecoder();
        type = new TypeReference<List<Car>>() {}.getType();
        break;
      case "iterator":
        decoder = JacksonIteratorDecoder.create();
        type = new TypeReference<Iterator<Car>>() {}.getType();
        break;
      case "stream":
        decoder = StreamDecoder.create(JacksonIteratorDecoder.create());
        type = new TypeReference<Stream<Car>>() {}.getType();
        break;
      default:
        throw new IllegalStateException("Unknown api: " + api);
    }
  }

  private String carsJson(int count) {
    String car = "{\"name\":\"c4\",\"manufacturer\":\"Citroën\"}";
    StringBuilder builder = new StringBuilder("[");
    builder.append(car);
    for (int i = 1; i < count; i++) {
      builder.append(",").append(car);
    }
    return builder.append("]").toString();
  }

  static class Car {
    public String name;
    public String manufacturer;
  }
}
